Template Repository
=====================

Running from package
---------------------------------

* get the ''template-repository.war'' file from the [Downloads] (https://github.com/downloads/abiquo/template-repository/template-reposiotry.war)
* check the default configuration is appropriate (create '/opt/template_repository' folder if necessary) 
* if you need to change some properties, unzip the war in order to edit ''ROOT/WEB-INF/application/conf/application.conf''
* put in the webapps folder of your favorite apps container (e.g. Tomcat)


Running with Play! Framework
---------------------------------

* checkout the code 'git clone git://github.com/abiquo/template-repository.git'

* Install [Play! Framework] (http://www.playframework.org)
** apt-get / brew 

* get the dependencies 'play dependencies'

* run 'play run template-repository/'

* http://localhost:9000

Configuration
--------------------
Edit the 'template-repository/conf/application.conf' file:

* ovfcatalog.repositoryPath: directory where the disk files are stored. (/opt/template_repository by default)
* by default uses embedded filesystem database (db=fs)
** db: database connection. (db=mysql:root:root@templaterepository)
** also use ''jpa.ddl=create'' for the first run


You can also change the binding port (http.port=9000)

Autodetect Disk Format Type 
--------------------
Feature powered by  http://diskid.frameos.org/

Security
--------------------
You can take advantage of OpenID by setting your mail domain in:
* organization.domain

Exporting
--------------
If you want to export the application to a standard war:
play war template-repository -o template-repository.war --zip

See other [dev options] (http://www.playframework.org/documentation/1.2.4/deployment)

Note: before exporting to war, edit the ''template-repository/conf/application.conf'' and set ''application.mode=prod''.

Develop
------------
To import as an Eclipse project simply do 'play eclipsify template-repository/' and import as a normal project
