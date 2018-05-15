/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ltbs.play.scaffold

import java.time.LocalDate

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import cats.{Semigroup, Monoid, Order}
import cats.data.{EitherT, RWST}
import cats.implicits._
import play.api._
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.http.Writeable
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Controller, Request, Result}
import play.twirl.api.Html


trait FormHtml[A] {
  def asHtmlForm(key: String, form: Form[A])(implicit messages: Messages): Html
}

package object webmonad {

  implicit val htmlSemigroup: Monoid[Html] = new Monoid[Html] {
    def combine(x: Html, y: Html): Html = Html(x.toString ++ y.toString)
    def empty: Html = Html("")
  }


  implicit val orderedLD: Order[LocalDate] = new Order[LocalDate] {
    def compare(a: LocalDate, b: LocalDate): Int = a compareTo b
  }

  // State is read once, threaded through the webmonads and the final state is
  // written back at the end of execution
  type DbState = Map[String, JsValue]

  // write out Pages (path state)
  type Path = List[String]

  type WebInner[A] = RWST[Future, (String, Request[AnyContent]), Path, (Path, DbState), A]
  type WebMonad[A] = EitherT[WebInner, Result, A]

  def webMonad[A](
    f: (String, Request[AnyContent], Path, DbState) => Future[(Option[String], Path, DbState, Either[Result, A])]
  )(implicit ec: ExecutionContext): WebMonad[A] =
    EitherT[WebInner, Result, A] {
      RWST { case ((pathA, r), (pathB, st)) =>
        f(pathA, r, pathB, st).map { case (a, b, c, d) => (a.toList, (b, c), d) }
      }
    }

  def getRequest(implicit ec: ExecutionContext): WebMonad[Request[AnyContent]] =
    webMonad { (a, request, path, db) =>
      (none[String], path, db, request.asRight).pure[Future]
    }

  def read[A](key: String)(implicit reads: Reads[A], ec: ExecutionContext): WebMonad[Option[A]] =
    webMonad { (_, _, path, db) =>
      (none[String], path, db, db.get(key).map {
        _.as[A]
      }.asRight[Result]).pure[Future]
    }

  def write[A](key: String, value: A)(implicit writes: Writes[A], ec: ExecutionContext): WebMonad[Unit] =
    webMonad { (_, _, path, db) =>
      (none[String], path, db + (key -> Json.toJson(value)), ().asRight[Result]).pure[Future]
    }

  def update[A](key: String)(
    f: Option[A] => Option[A]
  )(implicit format: Format[A], ec: ExecutionContext): WebMonad[Unit] = webMonad { (_, _, path, db) =>
    f(db.get(key).map {
      _.as[A]
    }) match {
      case Some(x) =>
        (none[String], path, db + (key -> Json.toJson(x)), ().asRight[Result]).pure[Future]
      case None =>
        (none[String], path, db - key, ().asRight[Result]).pure[Future]
    }
  }

  def clear(key: String)(implicit ec: ExecutionContext): WebMonad[Unit] =
    webMonad { (_, _, path, db) =>
      (none[String], path, db - key, ().asRight[Result]).pure[Future]
    }

  def cachedFuture[A]
    (cacheId: String)
    (f: => Future[A])
    (implicit format: Format[A], ec: ExecutionContext): WebMonad[A] =
    webMonad { (id, request, path, db) =>
      val cached = for {
        record <- db.get(cacheId)
      } yield
          (none[String], path, db, record.as[A].asRight[Result])
            .pure[Future]

      cached.getOrElse {
        f.map { result =>
          val newDb = db + (cacheId -> Json.toJson(result))
          (none[String], path, newDb, result.asRight[Result])
        }
      }
    }  

  def cachedFutureOpt[A]
    (cacheId: String)
    (f: => Future[Option[A]])
    (implicit format: Format[A], ec: ExecutionContext): WebMonad[Option[A]] =
    webMonad { (id, request, path, db) =>
      val cached = for {
        record <- db.get(cacheId)
      } yield
          (none[String], path, db, record.as[A].some.asRight[Result])
            .pure[Future]

      cached.getOrElse {
        f.map { result =>
          val newDb = result.map { r => 
            db + (cacheId -> Json.toJson(r))
          } getOrElse db
          (none[String], path, newDb, result.asRight[Result])
        }
      }
    }  


}

package webmonad {

  sealed trait Control
  //case class Delete(index: Int) extends Control
  case object Add extends Control
  case object Done extends Control
  case class Delete(i: Int) extends Control {
    override def toString = s"Delete.$i"
  }

  trait WebMonadController extends Controller with i18n.I18nSupport {

    implicit def ec: ExecutionContext

    def execute[A](f: => Future[A]): WebMonad[A] =
      webMonad{ (id, request, path, db) =>
        f.map{ e =>
          (none[String], path, db, e.asRight[Result])
        }
      }

    implicit def resultToWebMonad(result: Result): WebMonad[Result] =

      EitherT[WebInner, Result, Result] {
        RWST { case ((_, r), (path, st)) =>
          (
            List.empty[String],
            (path, st),
            result.asRight[Result]
          ).pure[Future]
        }
      }

    def redirect(target: String): WebMonad[Unit] = webMonad {
      (id, request, path, db) =>
      (none[String], path, db, Redirect(s"./$target").asLeft[Unit]).pure[Future]
    }

    def many[A](
      id: String,
      min: Int = 0,
      max: Int = 1000,
      default: List[A] = List.empty[A],
      deleteConfirmation: A => WebMonad[Boolean] = {_: A => true.pure[WebMonad]}
    )(listingPage: (String, Int, Int, List[A]) => WebMonad[Control])
      (wm: String => WebMonad[A])
      (implicit format: Format[A]): WebMonad[List[A]] =
    {
      val innerId = s"${id}_add"
      val innerPage: WebMonad[A] = wm(innerId)

      val dataKey = s"${id}_data"
      val updateProgram: WebMonad[List[A]] = for {
        addItem <- innerPage
        _ <- update[List[A]](dataKey) { x => {
          x.getOrElse(default) :+ addItem
        }.some }

        _ <- clear(innerId)
        i <- many(id, min, max)(listingPage)(wm)
      } yield i

      def deleteProgram(index: Int): WebMonad[List[A]] = for {
        allItems <- read[List[A]](dataKey).map{_.getOrElse(default)}
        _ <- update[List[A]](dataKey) { x => {
               val all = x.getOrElse(default)
               all.take(index) ++ all.drop(index + 1)
             }.some } when deleteConfirmation(allItems(index))
        _ <- redirect(id) 
      } yield {
        throw new IllegalStateException("Redirect failing for deletion!")
      }

      for {
        items <- read[List[A]](dataKey).map{_.getOrElse(default)}
        res <- listingPage(id,min,max,items).flatMap {
          case Add => updateProgram
          case Done => read[List[A]](dataKey).map {
            _.getOrElse(List.empty[A])
          }: WebMonad[List[A]]
          case Delete(index) => deleteProgram(index)
        }
      } yield (res)

    }

    def runInner(request: Request[AnyContent])(program: WebMonad[Result])(id: String)(
      load: String => Future[Map[String, JsValue]],
      save: (String, Map[String, JsValue]) => Future[Unit]
    ): Future[Result] = {
        request.session.get("uuid").fold {
          Redirect(".").withSession {
            request.session + ("uuid" -> java.util.UUID.randomUUID.toString)
          }.pure[Future]
        } { sessionUUID =>
          load(sessionUUID).flatMap { initialData =>

            def parse(in: String): Map[String, JsValue] =
              Json.parse(in) match {
                case JsObject(v) => v.toList.toMap
                case _ => throw new IllegalArgumentException
              }

            val data = request.getQueryString("restoreState")
              .fold(initialData)(parse)

            program.value
              .run((id, request), (List.empty[String], data))
              .flatMap { case (_, (path, state), a) => {
                if (state != initialData) {
                  println(s"Saving $sessionUUID to $state")
                  save(sessionUUID, state)
                }
                else
                  ().pure[Future]
              }.map { _ =>
                a.fold(identity, identity)
              }
              }
          }
        }
    }

    def run(program: WebMonad[Result])(id: String)(
      load: String => Future[Map[String, JsValue]],
      save: (String, Map[String, JsValue]) => Future[Unit]
    ) = Action.async {
      request => runInner(request)(program)(id)(load, save)
    }

    def formPage[A, B: Writeable](id: String)(
      mapping: Mapping[A], default: Option[A] = None
    )(
      render: (List[String], Form[A], Request[AnyContent]) => B
    )(implicit f: Format[A]): WebMonad[A] = {
      val form = Form(single(id -> mapping))
      val formWithDefault =
        default.map{form.fill}.getOrElse(form)

      EitherT[WebInner, Result, A] {
        RWST { case ((targetId, r), (path, st)) =>

          implicit val request: Request[AnyContent] = r

          val post = request.method.toLowerCase == "post"
          val method = request.method.toLowerCase
          val data = st.get(id)
          println(s"\n\n$method $id $targetId>> $data");
          {
            (method, data, targetId) match {
              // nothing in database, step in URI, render empty form
              case ("get", None, `id`) =>
                println("nothing in database, step in URI, render empty form")
                (
                  id.pure[List],
                  (path, st),
                  Ok(render(path, formWithDefault, implicitly)).asLeft[A]
                )
              // something in database, step in URI, user revisting old page, render filled in form
              case ("get", Some(json), `id`) =>
                println("something in database, step in URI, user revisting old page, render filled in form")
                (
                  id.pure[List],
                  (path, st),
                  Ok(render(path, form.fill(json.as[A]), implicitly)).asLeft[A]
                )
              // something in database, not step in URI, pass through
              case ("get", Some(json), _) =>
                println("something in database, not step in URI, pass through")
                (
                  id.pure[List],
                  (id :: path, st),
                  json.as[A].asRight[Result]
                )
              case ("post", _, `id`) => form.bindFromRequest.fold(
                formWithErrors => {
                  println("Error in form, bouncing back")
                  (
                    id.pure[List],
                    (path, st),
                    BadRequest(render(path, formWithErrors, implicitly)).asLeft[A]
                  )
                },
                formData => {
                  println(s"Storing $formData")
                  (
                    id.pure[List],
                    (id :: path, st + (id -> Json.toJson(formData))),
                    formData.asRight[Result]
                  )
                }
              )
              // something in database, previous page submitted
              case ("post", Some(json), _) if path.contains(targetId) =>
                println("something in database, previous page submitted")
                (
                  id.pure[List],
                  (id :: path, st),
                  Redirect(s"./$id").asLeft[A]
                )
              // something in database, posting, not step in URI nor previous page -> pass through
              case ("post", Some(json), _) =>
                println("something in database, posting, not step in URI nor previous page -> pass through")
                (
                  id.pure[List],
                  (id :: path, st),
                  json.as[A].asRight[Result]
                )
              case ("post", _, _) | ("get", _, _) =>
                println(s"Bad path $targetId, redirecting to $id")
                (
                  id.pure[List],
                  (path, st),
                  Redirect(s"./$id").asLeft[A]
                )
            }
          }.pure[Future]
        }
      }
    }

    implicit class RichWebMonadBoolean(wmb: WebMonad[Boolean]) {
      def andIfTrue[A](next: WebMonad[A]): WebMonad[Option[A]] = for {
        opt <- wmb
        ret <- if (opt) next map {_.some} else none[A].pure[WebMonad]
      } yield ret
    }

    implicit class RichWebMonoid[A](wm: WebMonad[A])(implicit monoid: Monoid[A]) {
      def emptyUnless(b: => Boolean): WebMonad[A] =
        if(b) wm else monoid.empty.pure[WebMonad]

      def emptyUnless(wmb: WebMonad[Boolean]): WebMonad[A] = for {
        opt <- wmb
        ret <- if (opt) wm else monoid.empty.pure[WebMonad]
      } yield ret
    }

    implicit class RichWebMonad[A](wm: WebMonad[A]) {
      def when(b: => Boolean): WebMonad[Option[A]] =
        if(b) wm.map{_.some} else none[A].pure[WebMonad]

      def when(wmb: WebMonad[Boolean]): WebMonad[Option[A]] = for {
        opt <- wmb
        ret <- if (opt) wm map {_.some} else none[A].pure[WebMonad]
      } yield ret
    }

    def when[A](b: => Boolean)(wm: WebMonad[A]): WebMonad[Option[A]] =
      if(b) wm.map{_.some} else none[A].pure[WebMonad]

  }

  /*
      def many[A, B: Writeable](id: String, min: Int = 0, max: Int = 1000)(singular: String => WebMonad[A])(implicit f: Format[A]): WebMonad[List[A]] = {

        sealed trait Control
        case class Delete(index: Int) extends Control
        case object Add extends Control
        case object Done extends Control

        def listingPage: WebMonad[Control] = ???
        val innerId = s"${id}_add"
        def innerPage: WebMonad[A] = singular(innerId)

        EitherT[WebInner, Result, A] {
          RWST { case ((targetId, r), (path,st)) =>

            implicit val request: Request[AnyContent] = r

            val post = request.method.toLowerCase == "post"
            val method = request.method.toLowerCase
            val data = st.get(id)
            println(s"$method $id $targetId");
            {
              (method, data, targetId) match {
                case ("get", None, `id`) =>
                case ("get", Some(json), `id`) =>
                case ("get", Some(json), _) =>
                case ("post", _, `id`) =>
                case ("post", Some(json), _) if path.contains(targetId) =>
                case ("post", Some(json), _) =>
                case ("post", _, _) | ("get", _, _) =>
                  println(s"Bad path $targetId, redirecting to $id")
                  (
                    id.pure[List],
                    (path, st),
                    Redirect(s"./$id"
                  ).asLeft[A]
              }
            }.pure[Future]
          }
        }
      }


    }
  */

}
