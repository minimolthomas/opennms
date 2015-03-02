/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2013-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/
package org.opennms.features.vaadin.surveillanceviews.ui.dashboard;

import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Button;
import com.vaadin.ui.Table;
import com.vaadin.ui.themes.BaseTheme;
import org.opennms.features.topology.api.support.InfoWindow;
import org.opennms.features.vaadin.surveillanceviews.service.SurveillanceViewService;
import org.opennms.netmgt.model.OnmsCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * This class represents a table displaying the node RTC calculations for the surveillance view's dashboard.
 *
 * @author Christian Pape
 */
public class SurveillanceViewNodeRtcTable extends SurveillanceViewDetailTable {
    /**
     * the logger instance
     */
    private static final Logger LOG = LoggerFactory.getLogger(SurveillanceViewNodeRtcTable.class);
    /**
     * the bean containeer for storing the RTC calculations
     */
    private BeanItemContainer<SurveillanceViewService.NodeRtc> m_beanItemContainer = new BeanItemContainer<SurveillanceViewService.NodeRtc>(SurveillanceViewService.NodeRtc.class);

    /**
     * Constructor for instantiating this component.
     *
     * @param surveillanceViewService the surveillance view service to be used
     * @param enabled                 the flag should links be enabled?
     */
    public SurveillanceViewNodeRtcTable(SurveillanceViewService surveillanceViewService, boolean enabled) {
        /**
         * call the super constructor
         */
        super("Outages", surveillanceViewService, enabled);

        /**
         * set the datasource
         */
        setContainerDataSource(m_beanItemContainer);

        /**
         * set the base style name
         */
        addStyleName("surveillance-view");

        /**
         * add node column
         */
        addGeneratedColumn("node", new ColumnGenerator() {
            @Override
            public Object generateCell(Table table, Object itemId, Object propertyId) {
                final SurveillanceViewService.NodeRtc nodeRtc = (SurveillanceViewService.NodeRtc) itemId;

                Button button = new Button(nodeRtc.getNode().getLabel());
                button.setPrimaryStyleName(BaseTheme.BUTTON_LINK);
                button.setEnabled(m_enabled);
                button.addStyleName("white");

                button.addClickListener(new Button.ClickListener() {
                    @Override
                    public void buttonClick(Button.ClickEvent clickEvent) {

                        final int nodeId = nodeRtc.getNode().getId();

                        final URI currentLocation = Page.getCurrent().getLocation();
                        final String contextRoot = VaadinServlet.getCurrent().getServletContext().getContextPath();
                        final String redirectFragment = contextRoot + "/element/node.jsp?quiet=true&node=" + nodeId;

                        LOG.debug("node {} clicked, current location = {}, uri = {}", nodeId, currentLocation, redirectFragment);

                        try {
                            SurveillanceViewNodeRtcTable.this.getUI().addWindow(new InfoWindow(new URL(currentLocation.toURL(), redirectFragment), new InfoWindow.LabelCreator() {
                                @Override
                                public String getLabel() {
                                    return "Node Info " + nodeId;
                                }
                            }));
                        } catch (MalformedURLException e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                });

                return button;
            }
        });

        /**
         * add currentOutages column
         */
        addGeneratedColumn("currentOutages", new ColumnGenerator() {
            @Override
            public Object generateCell(Table table, final Object itemId, Object columnId) {
                SurveillanceViewService.NodeRtc nodeRtc = (SurveillanceViewService.NodeRtc) itemId;
                return getImageSeverityLayout(nodeRtc.getDownServiceCount() + " of " + nodeRtc.getDownServiceCount());
            }
        });

        /**
         * add availability column
         */
        addGeneratedColumn("availability", new ColumnGenerator() {
            @Override
            public Object generateCell(Table table, final Object itemId, Object propertyId) {
                SurveillanceViewService.NodeRtc nodeRtc = (SurveillanceViewService.NodeRtc) itemId;
                return getImageSeverityLayout(nodeRtc.getAvailabilityAsString());
            }
        });

        /**
         * set cell style generator that handles the two severities for RTC calculations
         */
        setCellStyleGenerator(new CellStyleGenerator() {
            @Override
            public String getStyle(Table table, Object itemId, Object propertyId) {
                String style = null;
                SurveillanceViewService.NodeRtc nodeRtc = (SurveillanceViewService.NodeRtc) itemId;

                if (!"node".equals(propertyId)) {
                    if (nodeRtc.getAvailability() == 1.0) {
                        style = "rtc-normal";
                    } else {
                        style = "rtc-critical";
                    }
                }
                return style;
            }
        });

        /**
         * set the column headers
         */
        setColumnHeader("node", "Node");
        setColumnHeader("currentOutages", "Current Outages");
        setColumnHeader("availability", "24 Hour Availability");

        /**
         * define the visible columns
         */
        setVisibleColumns(new Object[]{"node", "currentOutages", "availability"});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void refreshDetails(Set<OnmsCategory> rowCategories, Set<OnmsCategory> colCategories) {
        /**
         * calculate and retrieve the RTC instances
         */
        List<SurveillanceViewService.NodeRtc> nodeRtcs = getSurveillanceViewService().getNoteRtcsForCategories(rowCategories, colCategories);

        /**
         * empty the container
         */
        m_beanItemContainer.removeAllItems();

        /**
         * add items to the container
         */
        if (nodeRtcs != null && !nodeRtcs.isEmpty()) {
            for (SurveillanceViewService.NodeRtc nodeRtc : nodeRtcs) {
                m_beanItemContainer.addItem(nodeRtc);
            }
        }

        /**
         * sort the iterms
         */
        sort(new Object[]{"node"}, new boolean[]{true});

        /**
         * refresh the table
         */
        refreshRowCache();
    }
}
