# Soft Drinks Industry Levy Frontend

[ ![Download](https://api.bintray.com/packages/hmrc/releases/soft-drinks-industry-levy-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/soft-drinks-industry-levy-frontend/_latestVersion)

## About
The Soft Drinks Industry Levy (SDIL) digital service is split into a number of different microservices all serving specific functions which are listed below:

**Liability tool** - Standalone frontend service that is used to check a company's liability in regards to the levy.

**Frontend** - The main frontend for the service which includes the pages for registration.

**Backend** - The service that the frontend uses to call HOD APIs to retrieve and send information relating to business information and subscribing to the levy.

**Stub** - Microservice that is used to mimic the DES APIs when running services locally or in the development and staging environments.

This is the main frontend, currently containing the registration form. 

For details about the sugar tax see [the GOV.UK guidance](https://www.gov.uk/guidance/soft-drinks-industry-levy)

## Running from source
Clone the repository using SSH:

`git@github.com:hmrc/soft-drinks-industry-levy-frontend.git`

If you need to setup SSH, see [the github guide to setting up SSH](https://help.github.com/articles/adding-a-new-ssh-key-to-your-github-account/)

Run the code from source using 

`sbt run`

Open your browser and navigate to the following url:

`http://localhost:8700/soft-drinks-industry-levy/register/start`

## Running through service manager

Run the following command in a terminal: `nano /home/<USER>/.sbt/.credentials`

See the output and ensure it is populated with the following details:

```
realm=Sonatype Nexus Repository Manager
host=NEXUS URL
user=USERNAME
password=PASSWORD
```

*You need to be on the VPN*

Ensure your service manager config is up to date, and run the following command:

`sm --start SDIL_ALL -f`

This will start all the required services

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
 