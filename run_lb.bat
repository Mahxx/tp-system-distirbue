@echo off
cd /d "%~dp0"
cls
echo =============================================================
echo  LOAD BALANCER COMPLET - Systeme de Messagerie BGH
echo  Architecture : 2 instances par protocole + equilibreur
echo =============================================================
echo.

echo [1/5] Compilation du projet...
call mvn compile dependency:copy-dependencies -q
if errorlevel 1 (
    echo ERREUR de compilation ! Verifiez mvn.
    pause & exit
)
echo      OK

echo.
echo [2/5] Demarrage du serveur d'authentification RMI...
start "Auth Server" java -cp "target\classes;target\dependency\*" org.example.AuthServer
ping 127.0.0.1 -n 4 > nul
echo      OK

echo.
echo [3/5] Demarrage des instances serveurs (2 par protocole)...
echo.
echo       SMTP  : Instance-1 port 2526 / Instance-2 port 2527
start "SMTP Instance-1 [2526]" java -cp "target\classes;target\dependency\*" org.example.SmtpServerGUI 2526
ping 127.0.0.1 -n 2 > nul
start "SMTP Instance-2 [2527]" java -cp "target\classes;target\dependency\*" org.example.SmtpServerGUI 2527

echo       POP3  : Instance-1 port 1111 / Instance-2 port 1112
ping 127.0.0.1 -n 2 > nul
start "POP3 Instance-1 [1111]" java -cp "target\classes;target\dependency\*" org.example.Pop3ServerGUI 1111
ping 127.0.0.1 -n 2 > nul
start "POP3 Instance-2 [1112]" java -cp "target\classes;target\dependency\*" org.example.Pop3ServerGUI 1112

echo       IMAP  : Instance-1 port 1431 / Instance-2 port 1432
ping 127.0.0.1 -n 2 > nul
start "IMAP Instance-1 [1431]" java -cp "target\classes;target\dependency\*" org.example.ImapServerGUI 1431
ping 127.0.0.1 -n 2 > nul
start "IMAP Instance-2 [1432]" java -cp "target\classes;target\dependency\*" org.example.ImapServerGUI 1432

echo       WEB   : Instance-1 port 8080 / Instance-2 port 8081
ping 127.0.0.1 -n 2 > nul
start "Web API Instance-1 [8080]" java -cp "target\classes;target\dependency\*" -Dserver.port=8080 org.example.web.WebApplication
ping 127.0.0.1 -n 4 > nul
start "Web API Instance-2 [8081]" java -cp "target\classes;target\dependency\*" -Dserver.port=8081 org.example.web.WebApplication

echo.
echo [4/4] Demarrage de NGINX n'est pas automatique.
echo      Vous devez le lancer vous-meme en parallele :
echo      1. Ouvrir PowerShell dans E:\nginx-1.30.0\
echo      2. Taper : .\nginx

echo.
echo =============================================================
echo  TOUT EST LANCE !
echo.
echo  Architecture deployee :
echo.
echo    [Clients]
echo        ^|
echo     [Port 25]  --^> TCP LB --^> SMTP-1(2526) / SMTP-2(2527)
echo     [Port 110] --^> TCP LB --^> POP3-1(1111) / POP3-2(1112)
echo     [Port 1430]--^> TCP LB --^> IMAP-1(1431) / IMAP-2(1432)
echo     [Port 80]  --^> NGINX  --^> Web-1(8080)  / Web-2(8081)
echo.
echo  ETAPE SUIVANTE : Dans chaque fenetre serveur, cliquez "Start Server"
echo  Puis lancez : test_load_balancer.bat
echo =============================================================
echo.
echo Appuyez sur une touche pour ouvrir le menu de tests...
pause > nul
start test_load_balancer.bat
