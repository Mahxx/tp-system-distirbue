@echo off
echo ==============================================================
echo  Demarrage du Serveur Web de Messagerie (Spring Boot REST API)
echo ==============================================================
echo.
echo  Note : L'AuthServer (RMI) et le Serveur SMTP (port 25) doivent
echo  etre en cours d'execution pour que l'API web fonctionne.
echo  Assurez-vous d'avoir lance 'run.bat' au prealable.
echo.
echo Compilation du projet en cours...
call mvn compile
echo.
echo Demarrage du serveur web sur http://localhost:8080 ...
java -cp "target\classes;target\dependency\*" org.example.web.WebApplication
pause
