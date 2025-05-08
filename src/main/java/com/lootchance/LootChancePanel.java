package com.lootchance;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;


import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LootChancePanel extends PluginPanel
{
    private final JTextField searchField = new JTextField();
    private final JButton clearButton = new JButton();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(listModel);
    private final JScrollPane scrollPane = new JScrollPane(resultList);
    private final JPanel infoPanel = new JPanel();
    private final JPanel dropsPanel = new JPanel();
    private final JPanel dropsWrapper = new JPanel(new BorderLayout());
    private final JScrollPane dropsScrollPane = new JScrollPane(dropsWrapper);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentContainer = new JPanel(contentLayout);
    JPanel calculatorPanel = new JPanel();
    private final List<Runnable> nextChanceUpdaters = new ArrayList<>();
    private final JTextField baseChanceField;
    private final JTextField tableChanceField;
    private final JTextField killCountField;
    private final JLabel resultLabel;
    private static final Logger log = LoggerFactory.getLogger(LootChancePanel.class);
    private boolean showPercentage = false;

    private List<String> npcNames = new ArrayList<>();

    @Inject
    public LootChancePanel()
    {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(300, 1000));
        add(buildTabBar(), BorderLayout.NORTH);
        add(contentContainer, BorderLayout.CENTER);

        // Create a stacked panel where infoPanel and scrollPane will overlap
        JPanel stackedPanel = new JPanel();
        stackedPanel.setLayout(new OverlayLayout(stackedPanel));

        // Ensure both components align to the same top-left corner
        scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        scrollPane.setAlignmentY(Component.TOP_ALIGNMENT);
        infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        dropsPanel.setLayout(new BoxLayout(dropsPanel, BoxLayout.Y_AXIS));
        dropsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        dropsPanel.setOpaque(false);

        dropsWrapper.setOpaque(false);
        dropsWrapper.add(dropsPanel, BorderLayout.NORTH);

        dropsScrollPane.setBorder(null);
        dropsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dropsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        dropsScrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        dropsScrollPane.setAlignmentY(Component.TOP_ALIGNMENT);
        dropsScrollPane.setPreferredSize(new Dimension(280, 800));
        dropsScrollPane.setVisible(false);

        // Add both components to the stack
        stackedPanel.add(scrollPane);
        stackedPanel.add(infoPanel);
        stackedPanel.add(dropsScrollPane);

        // Add searchField above, stackedPanel below
        JPanel lootView = new JPanel();
        lootView.setLayout(new BorderLayout());
        lootView.add(buildSearchBar(), BorderLayout.NORTH);
        lootView.add(stackedPanel, BorderLayout.CENTER);
        calculatorPanel.removeAll();
        calculatorPanel.setLayout(new BoxLayout(calculatorPanel, BoxLayout.Y_AXIS));
        calculatorPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        calculatorPanel.setBackground(getBackground());

        // === Header Title ===
        JPanel headerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        headerWrapper.setOpaque(false); // match background
        JLabel calcHeader = new JLabel("<html><h2>Probability Calculator</h2></html>");
        headerWrapper.add(calcHeader);
        calculatorPanel.add(headerWrapper);
        headerWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(Box.createVerticalStrut(8));

        // === Description ===
        JLabel calcIntro = new JLabel("<html><p>Estimate your chance of receiving at least one drop based on your Base Chance, Table Chance, and Kill / Loot Count.</p></html>");
        calcIntro.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(calcIntro);
        calculatorPanel.add(Box.createVerticalStrut(12));

        // === Usage Steps ===
        JLabel calcSteps = new JLabel("<html><ul>" +
                "<li>Enter the Base Chance (e.g. 1/128)</li>" +
                "<li>Enter the Table Chance (or use 1/1 if not applicable)</li>" +
                "<li>Enter the number of kills / loot attempts</li>" +
                "<li>The output shows the true base chance and your cumulative chance of at least one successful drop</li>" +
                "</ul></html>");
        calcSteps.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(calcSteps);
        calculatorPanel.add(Box.createVerticalStrut(12));

        // === Notes ===
        JLabel calcNotes = new JLabel("<html><i>Note: This is a basic probability estimate. It does not account for multiple rolls in a single drop(simplify them first), drop boosts, or unique table mechanics.</i></html>");
        calcNotes.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(calcNotes);
        calculatorPanel.add(Box.createVerticalStrut(20));

        JTextField[] baseFieldRef = new JTextField[1];
        JTextField[] tableFieldRef = new JTextField[1];
        JTextField[] killFieldRef = new JTextField[1];

        JPanel baseRow = buildLabeledField("Base Chance:", "1/100", baseFieldRef);
        JPanel tableRow = buildLabeledField("Table Chance:", "1/128", tableFieldRef);
        JPanel killRow = buildLabeledField("Kill / Loot Count:", "0", killFieldRef);

        baseChanceField = baseFieldRef[0];
        tableChanceField = tableFieldRef[0];
        killCountField = killFieldRef[0];

        calculatorPanel.add(baseRow);
        calculatorPanel.add(Box.createVerticalStrut(10));
        calculatorPanel.add(tableRow);
        calculatorPanel.add(Box.createVerticalStrut(10));
        calculatorPanel.add(killRow);
        calculatorPanel.add(Box.createVerticalStrut(10));

        JButton runButton = new JButton("Run");
        runButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        runButton.addActionListener(e -> runProbabilityCalculation());

        calculatorPanel.add(Box.createVerticalStrut(20));
        calculatorPanel.add(runButton);

        resultLabel = new JLabel("");
        resultLabel.setForeground(new Color(0, 255, 0));
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 14f));
        resultLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculatorPanel.add(Box.createVerticalStrut(10));
        calculatorPanel.add(resultLabel);

        contentContainer.add(lootView, "loot");
        JPanel calcWrapper = new JPanel(new BorderLayout());
        calcWrapper.setOpaque(false);
        calcWrapper.add(calculatorPanel, BorderLayout.NORTH); // anchor to top
        contentContainer.add(calcWrapper, "calculator");

        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("<html><h2>LootChance Plugin by Slurbz</h2></html>");
        JLabel version = new JLabel("ALPHA Version 0.1");
        JLabel desc = new JLabel("<html><p>Track cumulative drop chances based on table chance and kill/loot count using OSRS Wiki data.</p></html>");
        JLabel usage = new JLabel("<html><ul>" +
                "<li>Search for a lootable source (e.g. Zulrah, Chest (Barrows), etc)</li>" +
                "<li>Select it to view all item drop rates</li>" +
                "<li>Compare base chances to your kill/loot count</li>" +
                "</ul></html>");
        JLabel disclaimer = new JLabel("<html><i>"
                + "Note: Data is community-maintained and may contain inconsistencies.<br><br>"
                + "Chances are simplified for multiple rolls in a single drop.<br><br>"
                + "Chances assume full potential (e.g. Ring of Wealth, Barrows points, etc) with an exception to point-driven loot tables (e.g. Ancient Chest).<br><br>"
                + "In this case, check the item's table chance or use the Probability Calculator for custom math."
                + "</i></html>");

        infoPanel.add(title);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(version);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(desc);
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(usage);
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(disclaimer);

        // Center alignment
        infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (Component comp : infoPanel.getComponents())
        {
            if (comp instanceof JComponent)
            {
                ((JComponent) comp).setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }

        // Initially hide the scroll pane
        scrollPane.setVisible(false);

        // Load NPC names (fallback if needed)
        npcNames = getAllUniqueLootSources();

        // Show list only when focused
        searchField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent e)
            {
                scrollPane.setVisible(true);
                infoPanel.setVisible(true);
                dropsScrollPane.setVisible(false);  // hide drops when search is refocused
                filter();
                revalidate();
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                // Delay to allow click on resultList before hiding
                SwingUtilities.invokeLater(() ->
                {
                    if (!resultList.hasFocus())
                    {
                        scrollPane.setVisible(false);
                    }
                });
            }
        });

        // Filter list as text is typed
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { onSearchChange(); }
            public void removeUpdate(DocumentEvent e) { onSearchChange(); }
            public void changedUpdate(DocumentEvent e) { onSearchChange(); }
        });

        // Handle list selection
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.addListSelectionListener(e ->
        {
            if (!e.getValueIsAdjusting())
            {
                String selected = resultList.getSelectedValue();
                if (selected != null)
                {
                    searchField.setText(selected);
                    scrollPane.setVisible(false);
                    searchField.transferFocus();

                    infoPanel.setVisible(false);
                    dropsScrollPane.setVisible(true);
                    loadDropsFor(selected);
                }
            }
        });

        // hide if list loses focus too
        resultList.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                scrollPane.setVisible(false);
            }
        });
    }

    private void onSearchChange()
    {
        String text = searchField.getText();
        clearButton.setVisible(!text.isEmpty());
        filter();
    }

    private void filter()
    {
        String input = searchField.getText().toLowerCase();
        listModel.clear();

        npcNames.stream()
                .filter(name -> name.toLowerCase().contains(input))
                .limit(1500)
                .forEach(listModel::addElement);
    }

    private List<String> getAllUniqueLootSources()
    {
        List<String> names = new ArrayList<>();
        try
        {
            try (InputStream is = getClass().getResourceAsStream("/loot_sources.json"))
            {
                if (is == null)
                {
                    System.err.println("⚠ loot_sources.json not found in resources.");
                    return List.of("Barrows", "Kraken", "Vorkath"); // fallback
                }

                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonParser parser = new JsonParser();
                JsonArray sources = parser.parse(json).getAsJsonArray();

                for (int i = 0; i < sources.size(); i++)
                {
                    JsonObject source = sources.get(i).getAsJsonObject();
                    if (source.has("name"))
                    {
                        names.add(source.get("name").getAsString());
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed to load loot_sources.json", e);
            names = List.of("Barrows", "Kraken", "Vorkath"); // fallback
        }

        return names;
    }

    private void loadDropsFor(String npcName)
    {
        dropsPanel.removeAll();

        try (InputStream is = getClass().getResourceAsStream("/loot_sources.json"))
        {
            if (is == null)
            {
                log.warn("⚠ loot_sources.json not found in resources.");
                dropsPanel.add(new JLabel("⚠ Failed to load drop data (file not found)."));
                return;
            }

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonParser parser = new JsonParser();
            JsonArray sources = parser.parse(json).getAsJsonArray();

            for (int i = 0; i < sources.size(); i++)
            {
                JsonObject obj = sources.get(i).getAsJsonObject();
                if (obj.get("name").getAsString().equalsIgnoreCase(npcName))
                {
                    JsonArray drops = obj.getAsJsonArray("drops");
                    JLabel header = new JLabel(npcName);
                    header.setFont(header.getFont().deriveFont(Font.BOLD, 20f));
                    header.setForeground(Color.WHITE);
                    dropsPanel.add(header);
                    dropsPanel.add(Box.createVerticalStrut(10));

                    JButton percentToggleButton = new JButton(new ImageIcon(ImageUtil.loadImageResource(
                            LootChancePlugin.class, "/percentage_default_icon.png"
                    )));

                    percentToggleButton.setPreferredSize(new Dimension(32, 32));
                    percentToggleButton.setMaximumSize(new Dimension(32, 32));
                    percentToggleButton.setFocusPainted(false);
                    percentToggleButton.setBorderPainted(true);
                    percentToggleButton.setContentAreaFilled(true);
                    percentToggleButton.setToolTipText("Toggle between fraction and percentage");

                    // Initial border (dark gray)
                    percentToggleButton.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
                    percentToggleButton.setBackground(new Color(40, 40, 40)); // dark background

                    percentToggleButton.addActionListener(e -> {
                        showPercentage = !showPercentage;

                        // Update icon
                        String iconPath = showPercentage
                                ? "/percentage_toggled_icon.png"
                                : "/percentage_default_icon.png";

                        percentToggleButton.setIcon(new ImageIcon(ImageUtil.loadImageResource(LootChancePlugin.class, iconPath)));

                        // Update border color for toggle feedback
                        percentToggleButton.setBorder(BorderFactory.createLineBorder(
                                showPercentage ? new Color(120, 120, 120) : Color.DARK_GRAY, 2
                        ));

                        // Run all updaters to refresh labels
                        for (Runnable updater : nextChanceUpdaters)
                            updater.run();
                    });

                    // Add kill count spinner under the NPC name
                    JPanel killCountPanel = new JPanel();
                    killCountPanel.setLayout(new BoxLayout(killCountPanel, BoxLayout.X_AXIS));
                    killCountPanel.setOpaque(false);
                    killCountPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // prevents centering

                    JLabel killLabel = new JLabel("Kill / Loot Count:");
                    killLabel.setForeground(Color.WHITE);
                    killLabel.setFont(killLabel.getFont().deriveFont(Font.BOLD, 13f));

                    JSpinner killSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
                    killSpinner.setMaximumSize(new Dimension(80, 24));
                    JFormattedTextField tf = ((JSpinner.DefaultEditor) killSpinner.getEditor()).getTextField();

                    killSpinner.addChangeListener(e -> {
                        for (Runnable updater : nextChanceUpdaters) updater.run();
                    });

                    // Enforce numeric-only input and reset empty input to 0
                    tf.getDocument().addDocumentListener(new DocumentListener()
                    {
                        private void sanitize()
                        {
                            SwingUtilities.invokeLater(() -> {
                                String text = tf.getText().trim();
                                if (!text.matches("\\d+"))
                                {
                                    if (text.isEmpty())
                                    {
                                        tf.setText("0");
                                        killSpinner.setValue(0);
                                    }
                                    else
                                    {
                                        // Remove non-digit characters
                                        String digitsOnly = text.replaceAll("\\D", "");
                                        if (digitsOnly.isEmpty())
                                        {
                                            tf.setText("0");
                                            killSpinner.setValue(0);
                                        }
                                        else
                                        {
                                            tf.setText(digitsOnly);
                                            killSpinner.setValue(Integer.parseInt(digitsOnly));
                                        }
                                    }
                                }
                                for (Runnable updater : nextChanceUpdaters) updater.run();
                            });
                        }

                        public void insertUpdate(DocumentEvent e) { sanitize(); }
                        public void removeUpdate(DocumentEvent e) { sanitize(); }
                        public void changedUpdate(DocumentEvent e) { sanitize(); }
                    });
                    tf.setColumns(5);
                    tf.setHorizontalAlignment(JTextField.RIGHT);
                    tf.setFont(tf.getFont().deriveFont(Font.BOLD, 13f));

                    killCountPanel.add(killLabel);
                    killCountPanel.add(Box.createHorizontalStrut(8));
                    killCountPanel.add(killSpinner);
                    killCountPanel.add(Box.createHorizontalGlue());
                    JPanel toggleWrapper = new JPanel();
                    toggleWrapper.setLayout(new BorderLayout());
                    toggleWrapper.setOpaque(false);
                    toggleWrapper.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6)); // right padding of 6px
                    toggleWrapper.add(percentToggleButton, BorderLayout.CENTER);

                    killCountPanel.add(toggleWrapper);
                    killCountPanel.add(Box.createHorizontalStrut(8));

                    // Add to the drops panel below the NPC header
                    dropsPanel.add(killCountPanel);
                    dropsPanel.add(Box.createVerticalStrut(10));

                    // Group drops by category
                    Map<String, List<JsonObject>> grouped = new LinkedHashMap<>();

                    for (int j = 0; j < drops.size(); j++)
                    {
                        JsonObject drop = drops.get(j).getAsJsonObject();
                        String category = drop.has("category") ? drop.get("category").getAsString() : "Main";
                        grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(drop);
                    }

                    // For each category group
                    for (Map.Entry<String, List<JsonObject>> entry : grouped.entrySet())
                    {
                        String categoryName = entry.getKey();
                        List<JsonObject> dropList = entry.getValue();

                        // Collapse panel for this category
                        JPanel collapsiblePanel = new JPanel();
                        collapsiblePanel.setLayout(new BoxLayout(collapsiblePanel, BoxLayout.Y_AXIS));
                        collapsiblePanel.setOpaque(false);

                        // Create each drop row
                        for (JsonObject drop : dropList)
                        {
                            String itemName = drop.get("item").getAsString();
                            String qty = drop.get("quantity").getAsString();
                            String base = drop.get("chance").getAsString();
                            String table = drop.get("tableChance").getAsString();
                            String trueC = drop.get("trueChance").getAsString();
                            String rawPrice = drop.has("price") ? drop.get("price").getAsString() : null;
                            String formattedPrice = (rawPrice != null) ? formatPrice(rawPrice) : null;

                            // === Load icon ===
                            String cleanName = itemName.replaceAll("\\s*\\(.*?\\)", "").trim(); // remove anything in ( )
                            String fileName = cleanName.replace(" ", "_");
                            String iconPath = "/icons/" + fileName + ".png";
                            JLabel iconLabel = new JLabel();
                            iconLabel.setPreferredSize(new Dimension(32, 32));
                            iconLabel.setHorizontalAlignment(JLabel.CENTER);

                            try
                            {
                                ImageIcon icon = new ImageIcon(ImageUtil.loadImageResource(LootChancePlugin.class, iconPath));
                                iconLabel.setIcon(icon);
                            }
                            catch (Exception e)
                            {
                                // Icon not found — leave label blank
                            }
                            iconLabel.setPreferredSize(new Dimension(32, 32));
                            iconLabel.setHorizontalAlignment(JLabel.CENTER);

                            // === Item name and text ===
                            String itemHtml = String.format(
                                    "<html><span style='color:#FF981F;font-weight:bold;'>%s</span> <span style='color:#CCCCCC;'>×%s</span></html>",
                                    itemName, qty
                            );
                            JLabel nameLabel = new JLabel(itemHtml);
                            nameLabel.setFont(nameLabel.getFont().deriveFont(16f));

                            JLabel oddsLine1 = new JLabel();
                            oddsLine1.setFont(oddsLine1.getFont().deriveFont(14f));

                            JLabel oddsLine2 = new JLabel();
                            oddsLine2.setFont(oddsLine2.getFont().deriveFont(Font.BOLD, 14f));
                            oddsLine2.setForeground(Color.WHITE);

                            // === Layout ===
                            JPanel textCol = new JPanel();
                            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
                            textCol.setOpaque(false);
                            textCol.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0)); // Add 6px left padding
                            textCol.add(nameLabel);
                            if (formattedPrice != null)
                            {
                                JLabel priceLabel = new JLabel(formattedPrice);
                                priceLabel.setFont(priceLabel.getFont().deriveFont(Font.BOLD, 13f));
                                priceLabel.setForeground(new Color(0, 255, 255)); // Cyan
                                textCol.add(priceLabel);
                            }
                            textCol.add(oddsLine1);
                            textCol.add(oddsLine2);

                            JLabel nextChanceLabel = new JLabel();
                            nextChanceLabel.setFont(nextChanceLabel.getFont().deriveFont(Font.BOLD, 13f));
                            textCol.add(nextChanceLabel);

                            nextChanceUpdaters.add(() -> {
                                try {
                                    try {
                                        double baseVal = parseChanceInput(base);
                                        double tableVal = parseChanceInput(table);
                                        double trueVal = parseChanceInput(trueC);

                                        if (showPercentage) {
                                            String baseStr = String.format("%.2f%%", baseVal * 100);
                                            String tableStr = String.format("%.2f%%", tableVal * 100);
                                            String trueStr = String.format("%.2f%%", trueVal * 100);

                                            oddsLine1.setText(String.format(
                                                    "<html><span style='color:#CCCCCC;'>Base: %s | Table: %s</span></html>", baseStr, tableStr
                                            ));
                                            oddsLine2.setText(String.format(
                                                    "<html><span style='color:#FFFFFF;font-weight:bold;'>True Base: %s</span></html>", trueStr
                                            ));
                                        }
                                        else {
                                            oddsLine1.setText(String.format(
                                                    "<html><span style='color:#CCCCCC;'>Base: %s | Table: %s</span></html>", base, table
                                            ));
                                            oddsLine2.setText(String.format(
                                                    "<html><span style='color:#FFFFFF;font-weight:bold;'>True Base: %s</span></html>", trueC
                                            ));
                                        }
                                    } catch (Exception ignored) {}
                                    double trueBase = parseChanceInput(trueC);
                                    int killCount = (int) killSpinner.getValue();
                                    double nextChance = 1 - Math.pow(1 - trueBase, killCount + 1);

                                    if (showPercentage)
                                    {
                                        String percentString = String.format("%.2f", nextChance * 100);
                                        if (percentString.endsWith(".00")) percentString = percentString.substring(0, percentString.length() - 3);
                                        else if (percentString.endsWith("0")) percentString = percentString.substring(0, percentString.length() - 1);

                                        nextChanceLabel.setText(String.format(
                                                "<html><span style='color:#00FF00;'>Next Chance: %s%%</span></html>",
                                                percentString
                                        ));
                                    }
                                    else
                                    {
                                        double fraction = 1 / nextChance;
                                        String fracString = String.format("%.2f", fraction);
                                        if (fracString.endsWith(".00")) fracString = fracString.substring(0, fracString.length() - 3);
                                        else if (fracString.endsWith("0")) fracString = fracString.substring(0, fracString.length() - 1);

                                        nextChanceLabel.setText(String.format(
                                                "<html><span style='color:#00FF00;'>Next Chance: 1/%s</span></html>",
                                                fracString
                                        ));
                                    }
                                } catch (Exception ex) {
                                    nextChanceLabel.setText("");
                                }
                            });
                            nextChanceUpdaters.get(nextChanceUpdaters.size() - 1).run();

                            JPanel itemRow = new JPanel(new BorderLayout());
                            itemRow.setBackground(new Color(30, 30, 30));
                            itemRow.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
                            itemRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                            itemRow.add(iconLabel, BorderLayout.WEST);
                            itemRow.add(textCol, BorderLayout.CENTER);

                            // Prevent vertical stretching
                            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemRow.getPreferredSize().height));

                            collapsiblePanel.add(itemRow);
                            collapsiblePanel.add(Box.createVerticalStrut(4));
                        }

                        // Create the collapsible header (yellow arrow label)
                        String tableChance = "";
                        if (!dropList.isEmpty() && dropList.get(0).has("tableChance"))
                        {
                            tableChance = dropList.get(0).get("tableChance").getAsString();
                        }

                        String fractionHeader = categoryName;
                        String percentHeader = categoryName;

                        if (!tableChance.isEmpty())
                        {
                            try {
                                double val = parseChanceInput(tableChance);
                                String percentStr = String.format("%.2f%%", val * 100);
                                if (percentStr.endsWith(".00")) percentStr = percentStr.substring(0, percentStr.length() - 3);
                                else if (percentStr.endsWith("0")) percentStr = percentStr.substring(0, percentStr.length() - 1);

                                fractionHeader += " (" + tableChance + ")";
                                percentHeader += " (" + percentStr + ")";
                            } catch (Exception ignored) {
                                fractionHeader += " (" + tableChance + ")";
                                percentHeader += " (" + tableChance + ")";
                            }
                        }
                        final String finalFractionHeader = fractionHeader;
                        final String finalPercentHeader = percentHeader;

                        JLabel toggleLabel = new JLabel("▾ " + (showPercentage ? finalPercentHeader : finalFractionHeader));
                        Runnable toggleLabelUpdater = () ->
                                toggleLabel.setText((toggleLabel.getText().startsWith("▸ ") ? "▸ " : "▾ ") +
                                        (showPercentage ? finalPercentHeader : finalFractionHeader));
                        nextChanceUpdaters.add(toggleLabelUpdater);
                        toggleLabel.setForeground(new Color(255, 212, 0)); // OSRS yellow
                        toggleLabel.setFont(toggleLabel.getFont().deriveFont(Font.BOLD, 16f));
                        toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        toggleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

                        // Toggle logic
                        toggleLabel.addMouseListener(new java.awt.event.MouseAdapter()
                        {
                            private boolean expanded = true;
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent e)
                            {
                                expanded = !expanded;
                                collapsiblePanel.setVisible(expanded);
                                toggleLabel.setText((expanded ? "▾ " : "▸ ") + (showPercentage ? finalPercentHeader : finalFractionHeader));
                                dropsPanel.revalidate();
                                dropsPanel.repaint();
                            }
                        });

                        // Add the category to the drops panel
                        dropsPanel.add(toggleLabel);
                        dropsPanel.add(Box.createVerticalStrut(4));
                        dropsPanel.add(collapsiblePanel);
                        dropsPanel.add(Box.createVerticalStrut(8));
                    }

                    break;
                }
            }
        }
        catch (Exception e)
        {
            log.error("⚠ Failed to load drop data.", e);
            dropsPanel.add(new JLabel("⚠ Failed to load drop data (exception)."));
        }

        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    private JPanel buildSearchBar()
    {
        JPanel container = new JPanel(new BorderLayout(5, 0));
        container.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        container.setBackground(getBackground());

        // Search Icon (left)
        JLabel searchIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(LootChancePlugin.class, "/search_icon.png")));
        container.add(searchIcon, BorderLayout.WEST);

        // Search Field (center)
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        container.add(searchField, BorderLayout.CENTER);

        // Clear Button (right)
        clearButton.setIcon(new ImageIcon(ImageUtil.loadImageResource(LootChancePlugin.class, "/clear_icon.png")));
        clearButton.setPreferredSize(new Dimension(24, 24));
        clearButton.setContentAreaFilled(false);
        clearButton.setFocusPainted(false);
        clearButton.setBorderPainted(false);
        clearButton.setOpaque(false);
        clearButton.setToolTipText("Clear");
        clearButton.setVisible(false);  // hidden unless text is typed

        clearButton.addActionListener(e -> {
            searchField.setText("");
            clearButton.setVisible(false);
            infoPanel.setVisible(true);
            dropsScrollPane.setVisible(false);
            scrollPane.setVisible(false);
            filter();
        });

        container.add(clearButton, BorderLayout.EAST);
        return container;
    }

    private String formatPrice(String price)
    {
        try
        {
            if (price.contains("-"))
            {
                String[] parts = price.split("-");
                return formatSingle(parts[0]) + "-" + formatSingle(parts[1]) + " gp";
            }
            else
            {
                return formatSingle(price) + " gp";
            }
        }
        catch (Exception e)
        {
            return price + " gp";
        }
    }

    private String formatSingle(String numStr)
    {
        long value = Long.parseLong(numStr.trim());

        if (value >= 1_000_000_000)
            return String.format("%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000)
            return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 1_000)
            return String.format("%.2fK", value / 1_000.0);
        return String.valueOf(value);
    }

    private JPanel buildTabBar()
    {
        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new GridLayout(1, 2)); // Two side-by-side tabs
        tabPanel.setPreferredSize(new Dimension(300, 35));

        JButton lootTab = new JButton("Loot Sources");
        JButton calcTab = new JButton("Calculator");

        lootTab.setFocusPainted(false);
        calcTab.setFocusPainted(false);

        lootTab.setFont(lootTab.getFont().deriveFont(Font.BOLD, 16f));
        calcTab.setFont(calcTab.getFont().deriveFont(Font.BOLD, 16f));

        lootTab.setBackground(Color.DARK_GRAY);
        lootTab.setForeground(Color.WHITE);
        calcTab.setBackground(Color.DARK_GRAY);
        calcTab.setForeground(Color.WHITE);

        lootTab.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
        calcTab.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));

        lootTab.addActionListener(e -> {
            contentLayout.show(contentContainer, "loot");
            searchField.setVisible(true);
            searchField.setText("");
            scrollPane.setVisible(false);
            infoPanel.setVisible(true);
            dropsScrollPane.setVisible(false);
        });

        calcTab.addActionListener(e -> {
            contentLayout.show(contentContainer, "calculator");
            searchField.setVisible(false);
            clearButton.setVisible(false);
            scrollPane.setVisible(false);
            infoPanel.setVisible(false);
            dropsScrollPane.setVisible(false);
        });

        tabPanel.add(lootTab);
        tabPanel.add(calcTab);

        return tabPanel;
    }

    private JPanel buildLabeledField(String labelText, String placeholder, JTextField[] outField)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));

        JTextField input = new JTextField(placeholder);
        input.setMaximumSize(new Dimension(120, 24));
        input.setPreferredSize(new Dimension(120, 24));
        input.setHorizontalAlignment(JTextField.RIGHT);

        outField[0] = input;

        row.add(label);
        row.add(Box.createHorizontalStrut(10));
        row.add(input);

        return row;
    }

    private void runProbabilityCalculation()
    {
        try
        {
            double base = parseChanceInput(baseChanceField.getText());
            double table = parseChanceInput(tableChanceField.getText());
            int kills = Integer.parseInt(killCountField.getText().trim());

            double trueBase = base * table;
            double chance = 1 - Math.pow(1 - trueBase, kills + 1);

            DecimalFormat percentFormat = new DecimalFormat("0.0000");
            DecimalFormat fracFormat = new DecimalFormat("0.##"); // up to 2 decimals, no trailing zeroes

            String trueBaseStr = percentFormat.format(trueBase * 100);
            String trueBaseFrac = fracFormat.format(1 / trueBase);
            String chanceStr = percentFormat.format(chance * 100);
            String chanceFrac = fracFormat.format(1 / chance);

            resultLabel.setText(String.format(
                    "<html><div style='font-size:13px;'>"
                            + "True Base: (Base * Table)<br><b>%s%%</b> or <b>1/%s</b><br><br>"
                            + "Next Chance:<br><b>%s%%</b> or <b>1/%s</b>"
                            + "</div></html>",
                    trueBaseStr, trueBaseFrac,
                    chanceStr, chanceFrac
            ));
        }
        catch (Exception e)
        {
            resultLabel.setText("<html><span style='color:red;'>⚠ Invalid input.</span><br>Please check your fields.</html>");
        }
    }

    private double parseChanceInput(String input)
    {
        input = input.trim();

        // Reject standalone decimals like "0.008"
        if (!input.contains("/") && input.contains("."))
        {
            throw new IllegalArgumentException("Only fraction format (e.g., 1/128) is allowed.");
        }

        // Accept fractions like "1/128" or "1/42.67"
        if (input.contains("/"))
        {
            String[] parts = input.split("/");
            if (parts.length != 2)
            {
                throw new IllegalArgumentException("Invalid fraction format.");
            }

            String numerator = parts[0].trim();
            String denominator = parts[1].trim();

            // Deny if numerator is a decimal (e.g. "1.2/64")
            if (numerator.contains("."))
            {
                throw new IllegalArgumentException("Numerator must be a whole number.");
            }

            double num = Double.parseDouble(numerator);
            double den = Double.parseDouble(denominator);

            if (den == 0)
            {
                throw new ArithmeticException("Division by zero.");
            }

            return num / den;
        }

        // Anything else (like plain "1.25") is invalid
        throw new IllegalArgumentException("Only fraction format (e.g., 1/128 or 1/42.67) is allowed.");
    }
}
