@echo off
echo Compilation du projet et preparation des librairies...
call mvn compile dependency:copy-dependencies

echo =========================================
echo Demarrage du Serveur d'Authentification
echo =========================================
start "Serveur Authentification" java -cp "target\classes;target\dependency\*" org.example.AuthServer

echo Patientez 2 secondes...
ping 127.0.0.1 -n 3 > nul

echo =========================================
echo Demarrage des autres Serveurs et Client
echo =========================================
start "Serveur SMTP" java -cp "target\classes;target\dependency\*" org.example.SmtpServerGUI
start "Serveur POP3" java -cp "target\classes;target\dependency\*" org.example.Pop3ServerGUI
start "Serveur IMAP" java -cp "target\classes;target\dependency\*" org.example.ImapServerGUI
start "Admin Client" java -cp "target\classes;target\dependency\*" org.example.AdminClient
start "Client Messagerie" java -cp "target\classes;target\dependency\*" org.example.clients.MailClientGUI

echo Tous les composants sont lances dans des fenetres separees !
pause
