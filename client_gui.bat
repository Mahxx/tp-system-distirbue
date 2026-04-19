@echo off
cd /d "%~dp0"
echo Lancement de l'interface graphique client...
java -cp "target\classes;target\dependency\*" org.example.clients.MailClientGUI
