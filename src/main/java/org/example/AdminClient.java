package org.example;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * AdminClient — client d'administration RMI avec interface graphique.
 * Permet d'ajouter, modifier et supprimer des utilisateurs.
 *
 * Lancement : java -cp out org.example.AdminClient
 * (AuthServer doit être démarré en premier)
 */
public class AdminClient extends JFrame {

    private AuthService authService;

    private JTextField    hostField;
    private JButton       connectButton;
    private JLabel        statusLabel;

    private JTextField    usernameField;
    private JPasswordField passwordField;
    private JButton       addButton;
    private JButton       updateButton;
    private JButton       deleteButton;
    private JButton       checkButton;
    private JButton       listButton;

    private JTextArea logArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminClient().setVisible(true));
    }

    public AdminClient() {
        super("Administration RMI — Auth Server");
        setSize(620, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        add(buildConnectionPanel(), BorderLayout.NORTH);
        add(buildUserPanel(),       BorderLayout.CENTER);
        add(buildLogPanel(),        BorderLayout.SOUTH);

        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ //
    //  Construction de l'interface
    // ------------------------------------------------------------------ //

    private JPanel buildConnectionPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(BorderFactory.createTitledBorder("Connexion au serveur RMI"));

        p.add(new JLabel("Hôte :"));
        hostField = new JTextField("localhost", 14);
        p.add(hostField);

        connectButton = new JButton("Connecter");
        connectButton.addActionListener(e -> connect());
        p.add(connectButton);

        statusLabel = new JLabel("Non connecté");
        statusLabel.setForeground(Color.RED);
        p.add(statusLabel);
        return p;
    }

    private JPanel buildUserPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Gestion des comptes utilisateurs"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        p.add(new JLabel("Nom d'utilisateur :"), gc);
        gc.gridx = 1; gc.weightx = 1;
        usernameField = new JTextField(18);
        p.add(usernameField, gc);

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        p.add(new JLabel("Mot de passe :"), gc);
        gc.gridx = 1; gc.weightx = 1;
        passwordField = new JPasswordField(18);
        p.add(passwordField, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        addButton    = new JButton("Ajouter");
        updateButton = new JButton("Modifier");
        deleteButton = new JButton("Supprimer");
        checkButton  = new JButton("Vérifier");
        listButton   = new JButton("Lister");

        addButton.setBackground(new Color(60, 150, 80));
        addButton.setForeground(Color.WHITE);
        deleteButton.setBackground(new Color(190, 50, 50));
        deleteButton.setForeground(Color.WHITE);

        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.add(checkButton);
        buttons.add(listButton);

        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2;
        p.add(buttons, gc);

        addButton.addActionListener(   e -> addUser());
        updateButton.addActionListener(e -> updateUser());
        deleteButton.addActionListener(e -> deleteUser());
        checkButton.addActionListener( e -> checkUser());
        listButton.addActionListener(  e -> listUsers());

        setUserPanelEnabled(false);
        return p;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Journal des opérations"));
        logArea = new JTextArea(9, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        p.add(new JScrollPane(logArea));
        p.setPreferredSize(new Dimension(600, 190));
        return p;
    }

    // ------------------------------------------------------------------ //
    //  Actions
    // ------------------------------------------------------------------ //

    private void connect() {
        String host = hostField.getText().trim();
        try {
            Registry registry = LocateRegistry.getRegistry(host, AuthServer.RMI_PORT);
            authService = (AuthService) registry.lookup(AuthServer.SERVICE_NAME);
            statusLabel.setText("Connecté à " + host);
            statusLabel.setForeground(new Color(0, 130, 0));
            connectButton.setEnabled(false);
            setUserPanelEnabled(true);
            log("Connexion réussie au serveur RMI sur " + host + ":" + AuthServer.RMI_PORT);
        } catch (RemoteException | NotBoundException ex) {
            statusLabel.setText("Erreur de connexion");
            statusLabel.setForeground(Color.RED);
            log("Impossible de se connecter : " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Impossible de contacter le serveur RMI.\n" + ex.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addUser() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (!validateInput(user, pass)) return;
        try {
            if (authService.userExists(user)) {
                log("L'utilisateur '" + user + "' existe déjà.");
                return;
            }
            boolean ok = authService.createUser(user, pass);
            if (ok) log("Utilisateur '" + user + "' créé avec succès.");
            else    log("Erreur de base de données : l'utilisateur n'a pas pu être inséré (vérifiez la console du serveur d'authentification).");
        } catch (RemoteException e) { log("Erreur RMI : " + e.getMessage()); }
    }

    private void updateUser() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (!validateInput(user, pass)) return;
        try {
            boolean ok = authService.updateUser(user, pass);
            if (ok) log("Mot de passe de '" + user + "' mis à jour.");
            else    log("Utilisateur '" + user + "' introuvable.");
        } catch (RemoteException e) { log("Erreur RMI : " + e.getMessage()); }
    }

    private void deleteUser() {
        String user = usernameField.getText().trim();
        if (user.isEmpty()) { log("Nom d'utilisateur vide."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Supprimer l'utilisateur '" + user + "' ?",
            "Confirmation", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = authService.deleteUser(user);
            if (ok) log("Utilisateur '" + user + "' supprimé.");
            else    log("Utilisateur '" + user + "' introuvable.");
        } catch (RemoteException e) { log("Erreur RMI : " + e.getMessage()); }
    }

    private void checkUser() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (user.isEmpty()) { log("Nom d'utilisateur vide."); return; }
        try {
            boolean exists = authService.userExists(user);
            if (!exists) { log("Utilisateur '" + user + "' introuvable."); return; }
            if (!pass.isEmpty()) {
                boolean auth = authService.authenticate(user, pass);
                log(auth ? "Authentification réussie pour '" + user + "'."
                         : "Mot de passe incorrect pour '" + user + "'.");
            } else {
                log("Utilisateur '" + user + "' existe.");
            }
        } catch (RemoteException e) { log("Erreur RMI : " + e.getMessage()); }
    }

    private void listUsers() {
        try {
            java.util.List<String> users = authService.getAllUsers();
            if (users == null || users.isEmpty()) {
                log("Aucun utilisateur dans la base de données.");
            } else {
                log("Liste des utilisateurs (" + users.size() + ") : " + String.join(", ", users));
            }
        } catch (RemoteException e) {
            log("Erreur RMI (Lister) : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private boolean validateInput(String user, String pass) {
        if (user.isEmpty() || pass.isEmpty()) {
            log("Nom d'utilisateur et mot de passe requis.");
            return false;
        }
        return true;
    }

    private void setUserPanelEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        addButton.setEnabled(enabled);
        updateButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
        checkButton.setEnabled(enabled);
        listButton.setEnabled(enabled);
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}