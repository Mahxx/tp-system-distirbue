package org.example.clients;

import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.search.SubjectTerm;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Properties;

/**
 * MailClientGUI - Interface graphique unifiee pour les clients SMTP, POP3 et IMAP.
 * Regroupe dans une seule fenetre tous les clients de messagerie.
 */
public class MailClientGUI extends JFrame {

    // =========== CONFIGURATION ===========
    private static final String SMTP_HOST = "localhost";
    private static final int    SMTP_PORT = 25;
    private static final String POP3_HOST = "localhost";
    private static final int    POP3_PORT = 110;
    private static final String IMAP_HOST = "localhost";
    private static final int    IMAP_PORT = 1430;

    // =========== COULEURS (Thème Océan) ===========
    private static final Color BG_DARK    = new Color(15, 23, 42);      // bleu nuit profond
    private static final Color BG_PANEL   = new Color(23, 37, 64);      // bleu panel
    private static final Color ACCENT     = new Color(34, 211, 238);    // cyan éclatant
    private static final Color GREEN      = new Color(134, 239, 172);   // vert menthe
    private static final Color RED        = new Color(251, 146, 60);    // orange erreur
    private static final Color TEXT_MAIN  = new Color(226, 232, 240);   // argent clair
    private static final Color TEXT_SUB   = new Color(100, 116, 139);   // gris bleuté
    private static final Color ROW_ODD    = new Color(23, 37, 64);      // rangée impaire
    private static final Color ROW_EVEN   = new Color(15, 28, 52);      // rangée paire

    // =========== ETAT PARTAGÉ ===========
    private Store    imapStore;
    private Folder   imapFolder;
    private Message[] imapMessages;

    private Store    pop3Store;
    private Folder   pop3Folder;
    private Message[] pop3Messages;

    // =========== COMPOSANTS PARTAGÉS ===========
    private JTabbedPane tabs;
    private JTextArea   logArea;

    // ----- SMTP -----
    private JTextField smtpUser, smtpDest, smtpSubject;
    private JPasswordField smtpPass;
    private JTextArea      smtpBody;
    private JLabel         smtpStatus;

    // ----- POP3 -----
    private JTextField    pop3User;
    private JPasswordField pop3Pass;
    private JTable         pop3Table;
    private DefaultTableModel pop3Model;
    private JTextArea      pop3Content;
    private JLabel         pop3Status;

    // ----- IMAP -----
    private JTextField    imapUser;
    private JPasswordField imapPass;
    private JTextField    imapSearch;
    private JTable         imapTable;
    private DefaultTableModel imapModel;
    private JTextArea      imapContent;
    private JLabel         imapStatus;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Forcer toutes les couleurs Swing par défaut (élimine le blanc)
            UIManager.put("Panel.background",            new Color(15, 23, 42));
            UIManager.put("ScrollPane.background",       new Color(15, 23, 42));
            UIManager.put("Viewport.background",         new Color(15, 23, 42));
            UIManager.put("SplitPane.background",        new Color(15, 23, 42));
            UIManager.put("SplitPaneDivider.background", new Color(23, 37, 64));
            UIManager.put("TabbedPane.background",       new Color(15, 23, 42));
            UIManager.put("TabbedPane.selected",         new Color(23, 37, 64));
            UIManager.put("TabbedPane.foreground",       new Color(226, 232, 240));
            UIManager.put("TabbedPane.contentAreaColor",new Color(15, 23, 42));
            UIManager.put("TextArea.background",         new Color(23, 37, 64));
            UIManager.put("TextField.background",        new Color(23, 37, 64));
            UIManager.put("PasswordField.background",    new Color(23, 37, 64));
            UIManager.put("Table.background",            new Color(15, 23, 42));
            UIManager.put("TableHeader.background",      new Color(23, 37, 64));
            UIManager.put("OptionPane.background",       new Color(15, 23, 42));
            UIManager.put("OptionPane.messageForeground",new Color(226, 232, 240));
            new MailClientGUI().setVisible(true);
        });
    }

    public MailClientGUI() {
        super("Client de Messagerie — JavaMail");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        // Titre
        JLabel title = new JLabel("  ✉  Système de Messagerie", JLabel.LEFT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(34, 211, 238));        // cyan
        title.setBackground(new Color(8, 14, 28));           // bleu nuit très profond
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 0));
        add(title, BorderLayout.NORTH);

        // Onglets
        tabs = buildTabs();
        add(tabs, BorderLayout.CENTER);

        // Zone de logs en bas
        logArea = new JTextArea(5, 80);
        logArea.setEditable(false);
        logArea.setBackground(new Color(8, 14, 28));
        logArea.setForeground(TEXT_SUB);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JScrollPane logScroll = darkScroll(logArea);
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BG_PANEL));
        logScroll.setPreferredSize(new Dimension(0, 110));
        add(logScroll, BorderLayout.SOUTH);
    }

    // =========================================================
    //  CONSTRUCTION DES ONGLETS
    // =========================================================
    private JTabbedPane buildTabs() {
        JTabbedPane tp = new JTabbedPane();
        tp.setBackground(BG_DARK);
        tp.setForeground(TEXT_MAIN);
        tp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tp.addTab("  ✉  Envoyer (SMTP)  ", buildSmtpPanel());
        tp.addTab("  📥  Boite POP3  ",     buildPop3Panel());
        tp.addTab("  📬  Boite IMAP  ",     buildImapPanel());
        return tp;
    }

    // =========================================================
    //  PANEL SMTP
    // =========================================================
    private JPanel buildSmtpPanel() {
        JPanel p = darkPanel(new BorderLayout(12, 12));
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        // Formulaire
        JPanel form = darkPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        smtpUser    = styledField("votre_nom");
        smtpPass    = styledPass();
        smtpDest    = styledField("destinataire");
        smtpSubject = styledField("Sujet du message");
        smtpBody    = new JTextArea(6, 30);
        styledArea(smtpBody, "Corps du message...");

        addRow(form, g, 0, "Utilisateur :", smtpUser);
        addRow(form, g, 1, "Mot de passe :", smtpPass);
        addRow(form, g, 2, "Destinataire :", smtpDest);
        addRow(form, g, 3, "Sujet :", smtpSubject);

        g.gridx = 0; g.gridy = 4; form.add(label("Message :"), g);
        g.gridx = 1; g.gridy = 4; g.weightx = 1;
        form.add(darkScroll(smtpBody), g);
        g.weightx = 0;

        // Bouton
        JButton btn = bigButton("Envoyer le message", ACCENT);
        smtpStatus  = statusLabel("");
        btn.addActionListener(e -> doSmtpSend());

        JPanel south = darkPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(btn);
        south.add(Box.createHorizontalStrut(16));
        south.add(smtpStatus);

        p.add(form, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    // =========================================================
    //  PANEL POP3
    // =========================================================
    private JPanel buildPop3Panel() {
        JPanel p = darkPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        // Connexion
        JPanel conn = darkPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pop3User = styledField("utilisateur"); pop3User.setPreferredSize(new Dimension(130, 32));
        pop3Pass = styledPass();               pop3Pass.setPreferredSize(new Dimension(110, 32));
        JButton btnConn = bigButton("Se connecter", ACCENT);
        JButton btnDel  = bigButton("Supprimer", RED);
        pop3Status = statusLabel("Non connecté");
        btnConn.addActionListener(e -> doPop3Connect());
        btnDel.addActionListener(e  -> doPop3Delete());
        conn.add(label("Utilisateur :")); conn.add(pop3User);
        conn.add(label("Mot de passe :")); conn.add(pop3Pass);
        conn.add(btnConn); conn.add(btnDel); conn.add(pop3Status);

        // Table des messages
        pop3Model = new DefaultTableModel(new String[]{"#","De","Sujet","Taille"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        pop3Table = styledTable(pop3Model);
        pop3Table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) doPop3Read();
        });

        // Contenu
        pop3Content = new JTextArea();
        styledArea(pop3Content, "Sélectionnez un message pour lire son contenu...");
        pop3Content.setEditable(false);

        JSplitPane split = darkSplit(JSplitPane.VERTICAL_SPLIT,
            darkScroll(pop3Table), darkScroll(pop3Content));
        split.setDividerLocation(240);

        p.add(conn, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // =========================================================
    //  PANEL IMAP
    // =========================================================
    private JPanel buildImapPanel() {
        JPanel p = darkPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        // Connexion + actions
        JPanel conn = darkPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        imapUser   = styledField("utilisateur"); imapUser.setPreferredSize(new Dimension(130, 32));
        imapPass   = styledPass();               imapPass.setPreferredSize(new Dimension(110, 32));
        imapSearch = styledField("mot-clé sujet"); imapSearch.setPreferredSize(new Dimension(140, 32));
        JButton btnConn    = bigButton("Se connecter", ACCENT);
        JButton btnLu      = bigButton("Marquer LU", GREEN);
        JButton btnNonLu   = bigButton("Marquer NON-LU", new Color(250, 179, 135));
        JButton btnSearch  = bigButton("Rechercher", new Color(203, 166, 247));
        imapStatus = statusLabel("Non connecté");

        btnConn.addActionListener(e   -> doImapConnect());
        btnLu.addActionListener(e     -> doImapMark(true));
        btnNonLu.addActionListener(e  -> doImapMark(false));
        btnSearch.addActionListener(e -> doImapSearch());

        conn.add(label("Utilisateur :")); conn.add(imapUser);
        conn.add(label("Mot de passe :")); conn.add(imapPass);
        conn.add(btnConn);
        conn.add(Box.createHorizontalStrut(8));
        conn.add(btnLu); conn.add(btnNonLu);
        conn.add(Box.createHorizontalStrut(8));
        conn.add(imapSearch); conn.add(btnSearch);
        conn.add(imapStatus);

        // Table
        imapModel = new DefaultTableModel(new String[]{"#","Statut","De","Sujet"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        imapTable = styledTable(imapModel);
        imapTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) doImapRead();
        });

        // Contenu
        imapContent = new JTextArea();
        styledArea(imapContent, "Sélectionnez un message pour lire son contenu...");
        imapContent.setEditable(false);

        JSplitPane split = darkSplit(JSplitPane.VERTICAL_SPLIT,
            darkScroll(imapTable), darkScroll(imapContent));
        split.setDividerLocation(260);

        p.add(conn, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // =========================================================
    //  ACTIONS SMTP
    // =========================================================
    private void doSmtpSend() {
        String user = smtpUser.getText().trim();
        String pass = new String(smtpPass.getPassword()).trim();
        String dest = smtpDest.getText().trim();
        String subj = smtpSubject.getText().trim();
        String body = smtpBody.getText().trim();
        if (user.isEmpty() || dest.isEmpty()) {
            smtpStatus.setForeground(RED);
            smtpStatus.setText("Remplissez tous les champs !");
            return;
        }
        smtpStatus.setForeground(TEXT_SUB); smtpStatus.setText("Envoi en cours...");
        log("[SMTP] Connexion à " + SMTP_HOST + ":" + SMTP_PORT);
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                try {
                    Properties props = new Properties();
                    props.put("mail.smtp.host", SMTP_HOST);
                    props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtp.starttls.enable", "false");
                    props.put("mail.smtp.connectiontimeout", "5000");
                    props.put("mail.smtp.timeout", "5000");
                    Session s = Session.getInstance(props, new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(user, pass);
                        }
                    });
                    MimeMessage msg = new MimeMessage(s);
                    msg.setFrom(new InternetAddress(email(user)));
                    msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email(dest)));
                    msg.setSubject(subj, "UTF-8");
                    msg.setText(body, "UTF-8");
                    Transport.send(msg);
                    return "OK";
                } catch (AuthenticationFailedException e) {
                    return "AUTH:" + e.getMessage();
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }
            protected void done() {
                try {
                    String r = get();
                    if ("OK".equals(r)) {
                        smtpStatus.setForeground(GREEN);
                        smtpStatus.setText("✓ Message envoyé avec succès !");
                        log("[SMTP] Message envoyé de " + email(user) + " vers " + email(dest));
                        smtpBody.setText("");
                    } else if (r.startsWith("AUTH:")) {
                        smtpStatus.setForeground(RED);
                        smtpStatus.setText("✗ Identifiants incorrects");
                        log("[SMTP] Erreur auth : " + r);
                    } else {
                        smtpStatus.setForeground(RED);
                        smtpStatus.setText("✗ Erreur : " + r.substring(4, Math.min(r.length(), 60)));
                        log("[SMTP] Erreur : " + r);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // =========================================================
    //  ACTIONS POP3
    // =========================================================
    private void doPop3Connect() {
        String user = pop3User.getText().trim();
        String pass = new String(pop3Pass.getPassword()).trim();
        pop3Status.setForeground(TEXT_SUB); pop3Status.setText("Connexion...");
        log("[POP3] Connexion à " + POP3_HOST + ":" + POP3_PORT + " en tant que " + user);
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                try {
                    if (pop3Store != null && pop3Store.isConnected()) {
                        if (pop3Folder != null && pop3Folder.isOpen()) pop3Folder.close(true);
                        pop3Store.close();
                    }
                    Properties props = new Properties();
                    props.put("mail.pop3.host", POP3_HOST);
                    props.put("mail.pop3.port", String.valueOf(POP3_PORT));
                    props.put("mail.pop3.starttls.enable", "false");
                    props.put("mail.pop3.disablecapa", "true");
                    props.put("mail.pop3.disabletop", "true");
                    props.put("mail.pop3.connectiontimeout", "5000");
                    props.put("mail.pop3.timeout", "5000");
                    Session s = Session.getInstance(props, null);
                    pop3Store = s.getStore("pop3");
                    pop3Store.connect(POP3_HOST, user, pass);
                    pop3Folder = pop3Store.getFolder("INBOX");
                    pop3Folder.open(Folder.READ_WRITE);
                    pop3Messages = pop3Folder.getMessages();
                    return "OK:" + pop3Messages.length;
                } catch (AuthenticationFailedException e) {
                    return "AUTH";
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }
            protected void done() {
                try {
                    String r = get();
                    if (r.startsWith("OK:")) {
                        int n = Integer.parseInt(r.substring(3));
                        pop3Status.setForeground(GREEN);
                        pop3Status.setText("✓ Connecté — " + n + " message(s)");
                        log("[POP3] " + n + " message(s) trouvé(s)");
                        refreshPop3Table();
                    } else if ("AUTH".equals(r)) {
                        pop3Status.setForeground(RED);
                        pop3Status.setText("✗ Identifiants incorrects");
                    } else {
                        pop3Status.setForeground(RED);
                        pop3Status.setText("✗ " + r.substring(4, Math.min(r.length(), 50)));
                        log("[POP3] Erreur : " + r);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void refreshPop3Table() {
        pop3Model.setRowCount(0);
        if (pop3Messages == null) return;
        for (int i = 0; i < pop3Messages.length; i++) {
            try {
                String from = from(pop3Messages[i]);
                String subj = pop3Messages[i].getSubject();
                int    size = pop3Messages[i].getSize();
                pop3Model.addRow(new Object[]{i + 1, from, subj, size + " o"});
            } catch (Exception e) {
                pop3Model.addRow(new Object[]{i + 1, "?", "?", "?"});
            }
        }
    }

    private void doPop3Read() {
        int row = pop3Table.getSelectedRow();
        if (row < 0 || pop3Messages == null) return;
        try {
            Message msg = pop3Messages[row];
            StringBuilder sb = new StringBuilder();
            sb.append("Sujet  : ").append(msg.getSubject()).append("\n");
            sb.append("De     : ").append(from(msg)).append("\n");
            sb.append("Date   : ").append(msg.getSentDate()).append("\n");
            sb.append("Taille : ").append(msg.getSize()).append(" octets\n");
            sb.append("─".repeat(50)).append("\n");
            Object content = msg.getContent();
            sb.append(content != null ? content.toString().trim() : "[Pas de contenu]");
            pop3Content.setText(sb.toString());
            pop3Content.setCaretPosition(0);
            log("[POP3] Message " + (row + 1) + " lu.");
        } catch (Exception e) {
            pop3Content.setText("[Erreur de lecture] " + e.getMessage());
        }
    }

    private void doPop3Delete() {
        int row = pop3Table.getSelectedRow();
        if (row < 0 || pop3Messages == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez un message à supprimer.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Supprimer le message " + (row + 1) + " ?", "Confirmer", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            pop3Messages[row].setFlag(Flags.Flag.DELETED, true);
            if (pop3Folder != null && pop3Folder.isOpen()) pop3Folder.close(true);
            pop3Folder = pop3Store.getFolder("INBOX");
            pop3Folder.open(Folder.READ_WRITE);
            pop3Messages = pop3Folder.getMessages();
            pop3Status.setForeground(GREEN);
            pop3Status.setText("✓ Supprimé — " + pop3Messages.length + " message(s) restants");
            pop3Content.setText("");
            refreshPop3Table();
            log("[POP3] Message " + (row + 1) + " supprimé.");
        } catch (Exception e) {
            pop3Status.setForeground(RED);
            pop3Status.setText("✗ Erreur suppression");
            log("[POP3] Erreur : " + e.getMessage());
        }
    }

    // =========================================================
    //  ACTIONS IMAP
    // =========================================================
    private void doImapConnect() {
        String user = imapUser.getText().trim();
        String pass = new String(imapPass.getPassword()).trim();
        imapStatus.setForeground(TEXT_SUB); imapStatus.setText("Connexion...");
        log("[IMAP] Connexion à " + IMAP_HOST + ":" + IMAP_PORT + " en tant que " + user);
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                try {
                    if (imapStore != null && imapStore.isConnected()) {
                        if (imapFolder != null && imapFolder.isOpen()) imapFolder.close(false);
                        imapStore.close();
                    }
                    Properties props = new Properties();
                    props.put("mail.imap.host", IMAP_HOST);
                    props.put("mail.imap.port", String.valueOf(IMAP_PORT));
                    props.put("mail.imap.starttls.enable", "false");
                    props.put("mail.imap.connectiontimeout", "5000");
                    props.put("mail.imap.timeout",           "5000");
                    Session s = Session.getInstance(props, null);
                    imapStore = s.getStore("imap");
                    imapStore.connect(IMAP_HOST, user, pass);
                    imapFolder = imapStore.getFolder("INBOX");
                    imapFolder.open(Folder.READ_WRITE);
                    imapMessages = imapFolder.getMessages();
                    return "OK:" + imapMessages.length;
                } catch (AuthenticationFailedException e) {
                    return "AUTH";
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }
            protected void done() {
                try {
                    String r = get();
                    if (r.startsWith("OK:")) {
                        int n = Integer.parseInt(r.substring(3));
                        imapStatus.setForeground(GREEN);
                        imapStatus.setText("✓ Connecté — " + n + " message(s)");
                        log("[IMAP] " + n + " message(s) trouvé(s)");
                        refreshImapTable(imapMessages);
                    } else if ("AUTH".equals(r)) {
                        imapStatus.setForeground(RED);
                        imapStatus.setText("✗ Identifiants incorrects");
                        log("[IMAP] Erreur d'authentification");
                    } else {
                        imapStatus.setForeground(RED);
                        imapStatus.setText("✗ " + r.substring(4, Math.min(r.length(), 50)));
                        log("[IMAP] Erreur : " + r);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void refreshImapTable(Message[] msgs) {
        imapModel.setRowCount(0);
        if (msgs == null) return;
        for (int i = 0; i < msgs.length; i++) {
            try {
                boolean seen  = msgs[i].isSet(Flags.Flag.SEEN);
                String status = seen ? "✓ Lu" : "● Non lu";
                String from   = from(msgs[i]);
                String subj   = msgs[i].getSubject();
                imapModel.addRow(new Object[]{i + 1, status, from, subj});
            } catch (Exception e) {
                imapModel.addRow(new Object[]{i + 1, "?", "?", "?"});
            }
        }
    }

    private void doImapRead() {
        int row = imapTable.getSelectedRow();
        if (row < 0 || imapMessages == null) return;
        try {
            Message msg = imapMessages[row];
            StringBuilder sb = new StringBuilder();
            boolean seen = msg.isSet(Flags.Flag.SEEN);
            sb.append("Statut : ").append(seen ? "Lu" : "Non lu").append("\n");
            sb.append("Sujet  : ").append(msg.getSubject()).append("\n");
            sb.append("De     : ").append(from(msg)).append("\n");
            sb.append("Date   : ").append(msg.getSentDate()).append("\n");
            sb.append("─".repeat(50)).append("\n");
            Object content = msg.getContent();
            sb.append(content != null ? content.toString().trim() : "[Pas de contenu]");
            imapContent.setText(sb.toString());
            imapContent.setCaretPosition(0);
            // Marquer comme lu
            if (!seen) {
                msg.setFlag(Flags.Flag.SEEN, true);
                imapModel.setValueAt("✓ Lu", row, 1);
                log("[IMAP] Message " + (row + 1) + " marqué comme lu.");
            }
        } catch (Exception e) {
            imapContent.setText("[Erreur de lecture] " + e.getMessage());
        }
    }

    private void doImapMark(boolean seen) {
        int row = imapTable.getSelectedRow();
        if (row < 0 || imapMessages == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez un message.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            imapMessages[row].setFlag(Flags.Flag.SEEN, seen);
            imapModel.setValueAt(seen ? "✓ Lu" : "● Non lu", row, 1);
            log("[IMAP] Message " + (row + 1) + " marqué " + (seen ? "LU" : "NON LU") + ".");
        } catch (Exception e) {
            log("[IMAP] Erreur marquage : " + e.getMessage());
        }
    }

    private void doImapSearch() {
        String kw = imapSearch.getText().trim();
        if (kw.isEmpty() || imapFolder == null) return;
        log("[IMAP] Recherche par sujet : \"" + kw + "\"");
        new SwingWorker<Message[], Void>() {
            protected Message[] doInBackground() throws Exception {
                return imapFolder.search(new SubjectTerm(kw));
            }
            protected void done() {
                try {
                    Message[] found = get();
                    imapStatus.setForeground(GREEN);
                    imapStatus.setText("✓ " + found.length + " résultat(s) pour \"" + kw + "\"");
                    refreshImapTable(found);
                    log("[IMAP] " + found.length + " résultat(s) trouvé(s).");
                } catch (Exception e) {
                    log("[IMAP] Erreur recherche : " + e.getMessage());
                }
            }
        }.execute();
    }

    // =========================================================
    //  UTILITAIRES UI
    // =========================================================
    private JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG_DARK);
        return p;
    }

    private JTextField styledField(String placeholder) {
        JTextField f = new JTextField(18);
        f.setBackground(BG_PANEL);
        f.setForeground(TEXT_MAIN);
        f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(88, 91, 112), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setText(placeholder);
        f.setForeground(TEXT_SUB);
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (f.getText().equals(placeholder)) { f.setText(""); f.setForeground(TEXT_MAIN); }
            }
            public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) { f.setText(placeholder); f.setForeground(TEXT_SUB); }
            }
        });
        return f;
    }

    private JPasswordField styledPass() {
        JPasswordField f = new JPasswordField(12);
        f.setBackground(BG_PANEL);
        f.setForeground(TEXT_MAIN);
        f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(88, 91, 112), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return f;
    }

    private void styledArea(JTextArea a, String hint) {
        a.setBackground(BG_PANEL);
        a.setForeground(TEXT_MAIN);
        a.setCaretColor(ACCENT);
        a.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        a.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        a.setText(hint);
        a.setForeground(TEXT_SUB);
        a.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (a.getText().equals(hint)) { a.setText(""); a.setForeground(TEXT_MAIN); }
            }
        });
    }

    private JButton bigButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(BG_PANEL);
        b.setForeground(fg);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 40)); }
            public void mouseExited(MouseEvent e)  { b.setBackground(BG_PANEL); }
        });
        return b;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_SUB);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return l;
    }

    private JLabel statusLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_SUB);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        return l;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model) {
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) c.setBackground(row % 2 == 0 ? ROW_EVEN : ROW_ODD);
                else c.setBackground(new Color(34, 211, 238, 50));  // cyan semi-transparent
                c.setForeground(TEXT_MAIN);
                return c;
            }
        };
        t.setBackground(BG_DARK);
        t.setForeground(TEXT_MAIN);
        t.setGridColor(new Color(60, 62, 80));
        t.setRowHeight(26);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.getTableHeader().setBackground(BG_PANEL);
        t.getTableHeader().setForeground(ACCENT);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.setSelectionBackground(new Color(34, 211, 238, 50));
        t.setSelectionForeground(TEXT_MAIN);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return t;
    }

    private JScrollPane darkScroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBackground(BG_DARK);
        sp.getViewport().setBackground(BG_DARK);
        sp.setBorder(BorderFactory.createLineBorder(BG_PANEL, 1));
        sp.getVerticalScrollBar().setBackground(BG_PANEL);
        sp.getHorizontalScrollBar().setBackground(BG_PANEL);
        return sp;
    }

    private JSplitPane darkSplit(int orientation, Component top, Component bottom) {
        JSplitPane sp = new JSplitPane(orientation, top, bottom);
        sp.setBackground(BG_DARK);
        sp.setDividerSize(5);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    public void paint(Graphics g) {
                        g.setColor(BG_PANEL);
                        g.fillRect(0, 0, getSize().width, getSize().height);
                    }
                };
            }
        });
        return sp;
    }

    private void addRow(JPanel p, GridBagConstraints g, int row, String lbl, JComponent comp) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; p.add(label(lbl), g);
        g.gridx = 1; g.weightx = 1; p.add(comp, g); g.weightx = 0;
    }

    private String from(Message msg) {
        try {
            Address[] a = msg.getFrom();
            return (a != null && a.length > 0) ? a[0].toString() : "Inconnu";
        } catch (Exception e) { return "Inconnu"; }
    }

    private String email(String u) {
        return u.contains("@") ? u : u + "@example.com";
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
