@echo off
setlocal
cd /d "%~dp0"

:menu
cls
echo =========================================================
echo      SYSTEME DE MESSAGERIE - Clients JavaMail
echo =========================================================
echo.
echo  Comptes valides (dans la base de donnees) :
echo    karim/karim   karimoo/karim   karin/karin   admin/admin
echo.
echo  Ports des serveurs :
echo    SMTP : 25     POP3 : 110     IMAP : 1430
echo.
echo  IMPORTANT : Dans chaque fenetre serveur GUI, cliquez
echo  sur le bouton [Start Server] avant d'utiliser un client.
echo.
echo  [1] Client SMTP  - Envoyer un email
echo  [2] Client POP3  - Lire et supprimer des messages
echo  [3] Client IMAP  - Gestion avancee (flags, recherche)
echo  [0] Quitter
echo.
echo =========================================================
echo.
set /p choice="Votre choix (0-3) : "

if "%choice%"=="1" goto smtp
if "%choice%"=="2" goto pop3
if "%choice%"=="3" goto imap
if "%choice%"=="0" goto fin

echo.
echo Choix invalide. Entrez 0, 1, 2 ou 3.
pause
goto menu

:smtp
cls
echo =========================================================
echo   CLIENT SMTP - Envoi d'email (port 25)
echo =========================================================
echo.
java -cp "target\classes;target\dependency\*" org.example.clients.SmtpClient
echo.
pause
goto menu

:pop3
cls
echo =========================================================
echo   CLIENT POP3 - Boite de reception (port 110)
echo =========================================================
echo.
java -cp "target\classes;target\dependency\*" org.example.clients.Pop3Client
echo.
pause
goto menu

:imap
cls
echo =========================================================
echo   CLIENT IMAP - Gestion avancee (port 1430)
echo =========================================================
echo.
java -cp "target\classes;target\dependency\*" org.example.clients.ImapClient
echo.
pause
goto menu

:fin
echo.
echo Au revoir !
