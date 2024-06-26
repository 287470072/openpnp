/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui.importer;

import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;

import org.openpnp.Translations;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.model.Board;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Package;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class EagleMountsmdUlpImporter implements BoardImporter {
    private final static String NAME = "EAGLE mountsmd.ulp"; //$NON-NLS-1$
    private final static String DESCRIPTION = Translations.getString("EagleMountsmdUlpImporter.Importer.Description"); //$NON-NLS-1$

    private Board board;
    private File topFile, bottomFile;

    @Override
    public String getImporterName() {
        return NAME;
    }

    @Override
    public String getImporterDescription() {
        return DESCRIPTION;
    }

    @Override
    public Board importBoard(Frame parent) throws Exception {
        Dlg dlg = new Dlg(parent);
        dlg.setVisible(true);
        return board;
    }

    public static List<Placement> parseFile(File file, Side side, boolean createMissingParts)
            throws Exception {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        ArrayList<Placement> placements = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }

            // C1 41.91 34.93 180 0.1uF C0805
            // T10 21.59 14.22 90 SOT23-BEC
            // BOTTOM_RULER_ORGIN 87.00 49.00 0 RULER
            // Name, X, Y, Angle, Value, Package
            //  printf("%s %5.2f %5.2f %3.0f %s %s\n",
            //        E.name, u2mm((xmin + xmax)/2), u2mm((ymin + ymax)/2),
            //        E.angle, E.value, E.package.name);
            String[] fields = line.split("\\s+"); //$NON-NLS-1$
            Placement placement = new Placement(fields[0]);
            placement.setLocation(new Location(LengthUnit.Millimeters,
                    Double.parseDouble(fields[1]), Double.parseDouble(fields[2]), 0,
                    Double.parseDouble(fields[3])));
            Configuration cfg = Configuration.get();
            if (cfg != null && createMissingParts) {
                String value = null, packageId = null;
                if (fields.length > 4) {
                    value = fields[4].trim();
                }
                if (fields.length > 5) {
                    packageId = fields[5].trim();
                }

                if (packageId == null || packageId.isEmpty()) {
                    packageId = value;
                    value = null;
                }
                
                String partId = packageId;
                if (value != null && !value.isEmpty()) {
                    partId += "-" + value; //$NON-NLS-1$
                }
                Part part = cfg.getPart(partId);
                if (part == null) {
                    part = new Part(partId);
                    Package pkg = cfg.getPackage(packageId);
                    if (pkg == null) {
                        pkg = new Package(packageId);
                        cfg.addPackage(pkg);
                    }
                    part.setPackage(pkg);

                    cfg.addPart(part);
                }
                placement.setPart(part);

            }

            placement.setSide(side);
            placements.add(placement);
        }
        reader.close();
        return placements;
    }

    class Dlg extends JDialog {
        private JTextField textFieldTopFile;
        private JTextField textFieldBottomFile;
        private final Action browseTopFileAction = new SwingAction();
        private final Action browseBottomFileAction = new SwingAction_1();
        private final Action importAction = new SwingAction_2();
        private final Action cancelAction = new SwingAction_3();
        private JCheckBox chckbxCreateMissingParts;

        public Dlg(Frame parent) {
            super(parent, DESCRIPTION, true);
            getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

            JPanel panel = new JPanel();
            panel.setBorder(new TitledBorder(null, Translations.getString("EagleMountsmdUlpImporter.PanelFiles.Border.title"), TitledBorder.LEADING, TitledBorder.TOP, //$NON-NLS-1$
                    null, null));
            getContentPane().add(panel);
            panel.setLayout(new FormLayout(
                    new ColumnSpec[] {FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                            FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("default:grow"), //$NON-NLS-1$
                            FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,},
                    new RowSpec[] {FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                            FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

            JLabel lblTopFilemnt = new JLabel(Translations.getString("EagleMountsmdUlpImporter.PanelFiles.topFilmentLabel.text")); //$NON-NLS-1$
            panel.add(lblTopFilemnt, "2, 2, right, default"); //$NON-NLS-1$

            textFieldTopFile = new JTextField();
            panel.add(textFieldTopFile, "4, 2, fill, default"); //$NON-NLS-1$
            textFieldTopFile.setColumns(10);

            JButton btnBrowse = new JButton(Translations.getString("EagleMountsmdUlpImporter.FilesPanel.browseButton.text")); //$NON-NLS-1$
            btnBrowse.setAction(browseTopFileAction);
            panel.add(btnBrowse, "6, 2"); //$NON-NLS-1$

            JLabel lblBottomFilemnb = new JLabel(Translations.getString("EagleMountsmdUlpImporter.FilesPanel.bottomFileLabel.text")); //$NON-NLS-1$
            panel.add(lblBottomFilemnb, "2, 4, right, default"); //$NON-NLS-1$

            textFieldBottomFile = new JTextField();
            panel.add(textFieldBottomFile, "4, 4, fill, default"); //$NON-NLS-1$
            textFieldBottomFile.setColumns(10);

            JButton btnBrowse_1 = new JButton(Translations.getString("EagleMountsmdUlpImporter.FilesPanel.browseButton2.text")); //$NON-NLS-1$
            btnBrowse_1.setAction(browseBottomFileAction);
            panel.add(btnBrowse_1, "6, 4"); //$NON-NLS-1$

            JPanel panel_1 = new JPanel();
            panel_1.setBorder(new TitledBorder(null, Translations.getString("EagleMountsmdUlpImporter.OptionsPanel.Border.title"), TitledBorder.LEADING, //$NON-NLS-1$
                    TitledBorder.TOP, null, null));
            getContentPane().add(panel_1);
            panel_1.setLayout(new FormLayout(
                    new ColumnSpec[] {FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,},
                    new RowSpec[] {FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

            chckbxCreateMissingParts = new JCheckBox(Translations.getString("EagleMountsmdUlpImporter.OptionsPanel.createMissingPartsChkbox.text")); //$NON-NLS-1$
            chckbxCreateMissingParts.setSelected(true);
            panel_1.add(chckbxCreateMissingParts, "2, 2"); //$NON-NLS-1$

            JSeparator separator = new JSeparator();
            getContentPane().add(separator);

            JPanel panel_2 = new JPanel();
            FlowLayout flowLayout = (FlowLayout) panel_2.getLayout();
            flowLayout.setAlignment(FlowLayout.RIGHT);
            getContentPane().add(panel_2);

            JButton btnCancel = new JButton(Translations.getString("EagleMountsmdUlpImporter.ButtonsPanel.cancelButton.text")); //$NON-NLS-1$
            btnCancel.setAction(cancelAction);
            panel_2.add(btnCancel);

            JButton btnImport = new JButton(Translations.getString("EagleMountsmdUlpImporter.ButtonsPanel.importButton.text")); //$NON-NLS-1$
            btnImport.setAction(importAction);
            panel_2.add(btnImport);

            setSize(400, 400);
            setLocationRelativeTo(parent);

            JRootPane rootPane = getRootPane();
            KeyStroke stroke = KeyStroke.getKeyStroke("ESCAPE"); //$NON-NLS-1$
            InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            inputMap.put(stroke, "ESCAPE"); //$NON-NLS-1$
            rootPane.getActionMap().put("ESCAPE", cancelAction); //$NON-NLS-1$
        }

        private class SwingAction extends AbstractAction {
            public SwingAction() {
                putValue(NAME, Translations.getString("EagleMountsmdUlpImporter.BrowseAction.Name")); //$NON-NLS-1$
                putValue(SHORT_DESCRIPTION, Translations.getString("EagleMountsmdUlpImporter.BrowseAction.ShortDescription")); //$NON-NLS-1$
            }

            public void actionPerformed(ActionEvent e) {
                FileDialog fileDialog = new FileDialog(Dlg.this);
                fileDialog.setFilenameFilter(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".mnt"); //$NON-NLS-1$
                    }
                });
                fileDialog.setVisible(true);
                if (fileDialog.getFile() == null) {
                    return;
                }
                File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());
                textFieldTopFile.setText(file.getAbsolutePath());
            }
        }

        private class SwingAction_1 extends AbstractAction {
            public SwingAction_1() {
                putValue(NAME, Translations.getString("EagleMountsmdUlpImporter.BrowseAction2.Name")); //$NON-NLS-1$
                putValue(SHORT_DESCRIPTION, Translations.getString("EagleMountsmdUlpImporter.BrowseAction2.ShortDescription")); //$NON-NLS-1$
            }

            public void actionPerformed(ActionEvent e) {
                FileDialog fileDialog = new FileDialog(Dlg.this);
                fileDialog.setFilenameFilter(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".mnb"); //$NON-NLS-1$
                    }
                });
                fileDialog.setVisible(true);
                if (fileDialog.getFile() == null) {
                    return;
                }
                File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());
                textFieldBottomFile.setText(file.getAbsolutePath());
            }
        }

        private class SwingAction_2 extends AbstractAction {
            public SwingAction_2() {
                putValue(NAME, Translations.getString("EagleMountsmdUlpImporter.ImportAction.Name")); //$NON-NLS-1$
                putValue(SHORT_DESCRIPTION, Translations.getString("EagleMountsmdUlpImporter.ImportAction.ShortDescription")); //$NON-NLS-1$
            }

            public void actionPerformed(ActionEvent e) {
                topFile = new File(textFieldTopFile.getText());
                bottomFile = new File(textFieldBottomFile.getText());
                board = new Board();
                List<Placement> placements = new ArrayList<>();
                try {
                    if (topFile.exists()) {
                        placements.addAll(parseFile(topFile, Side.Top,
                                chckbxCreateMissingParts.isSelected()));
                    }
                    if (bottomFile.exists()) {
                        placements.addAll(parseFile(bottomFile, Side.Bottom,
                                chckbxCreateMissingParts.isSelected()));
                    }
                }
                catch (Exception e1) {
                    MessageBoxes.errorBox(Dlg.this, Translations.getString("EagleMountsmdUlpImporter.ImportErrorMessage"), e1); //$NON-NLS-1$
                    return;
                }
                for (Placement placement : placements) {
                    board.addPlacement(placement);
                }
                setVisible(false);
            }
        }

        private class SwingAction_3 extends AbstractAction {
            public SwingAction_3() {
                putValue(NAME, Translations.getString("EagleMountsmdUlpImporter.CancelAction.Name")); //$NON-NLS-1$
                putValue(SHORT_DESCRIPTION, Translations.getString("EagleMountsmdUlpImporter.CancelAction.ShortDescription")); //$NON-NLS-1$
            }

            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        }
    }
}
