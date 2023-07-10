package org.openpnp.gui;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.support.IdentifiableTableCellRenderer;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.Configuration;
import org.openpnp.model.Package;
import org.openpnp.model.Part;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.PartAlignment;
import org.pmw.tinylog.Logger;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;


public class OtherPanel extends JPanel implements WizardContainer {

    private JTable table;

    final private Configuration configuration;
    final private Frame frame;


    public OtherPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;


        table = new AutoSelectTextTable();
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        table.setDefaultRenderer(org.openpnp.model.Package.class,
                new IdentifiableTableCellRenderer<Package>());

        table.add(Translations.getString("PartsPanel.SettingsTab.title"), //$NON-NLS-1$
                new JScrollPane(new OtherSettingPanel()));

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                firePartSelectionChanged();
            }
        });

    }

    public void firePartSelectionChanged() {
        Logger.trace("老铁双击666");         //$NON-NLS-1$

    }


    @Override
    public void wizardCompleted(Wizard wizard) {

    }

    @Override
    public void wizardCancelled(Wizard wizard) {

    }
}
