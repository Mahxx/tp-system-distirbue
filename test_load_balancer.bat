@echo off
setlocal
cd /d "%~dp0"

:menu
cls
echo =============================================================
echo   TESTS DE LOAD BALANCING - Systeme de Messagerie BGH
echo =============================================================
echo.
echo   PREREQUIS : run_lb.bat + HAProxy + NGINX doivent tourner
echo.
echo   1. Scenario 1 : Repartition simple (plusieurs requetes)
echo   2. Scenario 2 : Charge concurrente (plusieurs clients)
echo   3. Scenario 3 : Test de saturation (augmentation progressive)
echo   4. Scenario 4 : Test panne d'un noeud (IMAP Instance-2)
echo   5. Scenario 5 : Reprise apres panne
echo   6. Test Apache Bench sur l'API Web
echo   7. Quitter
echo =============================================================
echo.
set /p choice="Votre choix (1-7) : "

if "%choice%"=="1" goto scenario1
if "%choice%"=="2" goto scenario2
if "%choice%"=="3" goto scenario3
if "%choice%"=="4" goto scenario4
if "%choice%"=="5" goto scenario5
if "%choice%"=="6" goto apache_bench
if "%choice%"=="7" goto fin
goto menu

:scenario1
cls
echo =============================================================
echo  SCENARIO 1 : Repartition simple des requetes IMAP
echo  Envoie 10 connexions successives vers le load balancer
echo  Verifiez les fenetres "IMAP Instance-1 [1431]" et
echo                         "IMAP Instance-2 [1432]" pour
echo  confirmer la distribution Round Robin.
echo =============================================================
echo.
echo Envoi de 10 connexions IMAP successives via HAProxy (port 1430)...
echo.

java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario1

echo.
pause
goto menu

:scenario2
cls
echo =============================================================
echo  SCENARIO 2 : Charge concurrente
echo  Lance 5 threads client simultanement
echo =============================================================
echo.
java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario2
echo.
pause
goto menu

:scenario3
cls
echo =============================================================
echo  SCENARIO 3 : Saturation progressive
echo  Increase de 2 a 20 connexions avec mesure du temps
echo =============================================================
echo.
java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario3
echo.
pause
goto menu

:scenario4
cls
echo =============================================================
echo  SCENARIO 4 : Panne d'un noeud
echo  Simule l'arret de IMAP Instance-2 (port 1432)
echo  Verifiez que le trafic bascule vers Instance-1 (port 1431)
echo =============================================================
echo.
echo [!] ACTION REQUISE : Fermez manuellement la fenetre
echo     "IMAP Instance-2 [1432]" avec la croix rouge.
echo.
echo Appuyez sur une touche APRES avoir ferme la fenetre...
pause
echo.
echo Envoi de 6 connexions vers le LB (doit aller vers Instance-1)...
java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario1
echo.
echo Verifiez que toutes les requetes ont ete traitees par Instance-1.
pause
goto menu

:scenario5
cls
echo =============================================================
echo  SCENARIO 5 : Reprise apres panne
echo  Redemarrage de Instance-2 et reintegration dans la rotation
echo =============================================================
echo.
echo [!] ACTION REQUISE : Relancez IMAP Instance-2 avec la commande :
echo     java -cp "target\classes;target\dependency\*" org.example.ImapServerGUI 1432
echo.
echo Puis appuyez sur Start Server dans la nouvelle fenetre.
echo Appuyez sur une touche quand Instance-2 est prete...
pause
echo.
echo Envoi de 8 connexions (doit alterner entre les 2 instances)...
java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario1
echo.
echo Verifiez la distribution dans les 2 fenetres IMAP.
pause
goto menu

:apache_bench
cls
echo =============================================================
echo  TEST NGINX WEB API - Port 80 (HTTP)
echo  100 requetes, 10 concurrentes
echo  NGINX doit distribuer entre instances 8080 et 8081
echo =============================================================
echo.
echo Lancement du simulateur de charge (equivalent a Apache Bench)...
java -cp "target\classes;target\dependency\*" org.example.clients.LoadTestScenario6
echo.
pause
goto menu

:fin
echo.
echo Fin des tests.
