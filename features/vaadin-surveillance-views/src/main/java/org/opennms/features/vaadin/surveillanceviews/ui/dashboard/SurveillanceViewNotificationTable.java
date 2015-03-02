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
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.themes.BaseTheme;
import org.opennms.features.topology.api.support.InfoWindow;
import org.opennms.features.vaadin.surveillanceviews.service.SurveillanceViewService;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * This class represents a table for displaying the notifications for a surveillance view dashboard.
 *
 * @author Christian Pape
 */
public class SurveillanceViewNotificationTable extends SurveillanceViewDetailTable {
    /**
     * the logger instance
     */
    private static final Logger LOG = LoggerFactory.getLogger(SurveillanceViewNotificationTable.class);
    /**
     * the bean container for storing notifications
     */
    private BeanItemContainer<OnmsNotification> m_beanItemContainer = new BeanItemContainer<OnmsNotification>(OnmsNotification.class);
    /**
     * the custom severity map
     */
    private HashMap<OnmsNotification, String> m_customSeverity = new HashMap<>();

    /**
     * Constructor for instantiating this component.
     *
     * @param surveillanceViewService the surveillance view service to be used
     * @param enabled                 the flag should links be enabled?
     */
    public SurveillanceViewNotificationTable(SurveillanceViewService surveillanceViewService, boolean enabled) {
        /**
         * calling the super constructor
         */
        super("Notifications", surveillanceViewService, enabled);

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
            public Object generateCell(final Table table, final Object itemId, final Object propertyId) {
                final OnmsNotification onmsNotification = (OnmsNotification) itemId;

                Button icon = getClickableIcon("glyphicon glyphicon-bell", new Button.ClickListener() {
                    @Override
                    public void buttonClick(Button.ClickEvent clickEvent) {

                        OnmsNotification onmsNotification = (OnmsNotification) itemId;
                        final int notificationId = onmsNotification.getNotifyId();

                        final URI currentLocation = Page.getCurrent().getLocation();
                        final String contextRoot = VaadinServlet.getCurrent().getServletContext().getContextPath();
                        final String redirectFragment = contextRoot + "/notification/detail.jsp?quiet=true&notice=" + notificationId;

                        LOG.debug("notification {} clicked, current location = {}, uri = {}", notificationId, currentLocation, redirectFragment);

                        try {
                            SurveillanceViewNotificationTable.this.getUI().addWindow(new InfoWindow(new URL(currentLocation.toURL(), redirectFragment), new InfoWindow.LabelCreator() {
                                @Override
                                public String getLabel() {
                                    return "Notification Info " + notificationId;
                                }
                            }));
                        } catch (MalformedURLException e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                });

                Button button = new Button(onmsNotification.getNodeLabel());
                button.setPrimaryStyleName(BaseTheme.BUTTON_LINK);
                button.setEnabled(m_enabled);

                button.addClickListener(new Button.ClickListener() {
                    @Override
                    public void buttonClick(Button.ClickEvent clickEvent) {

                        final int nodeId = onmsNotification.getNodeId();

                        final URI currentLocation = Page.getCurrent().getLocation();
                        final String contextRoot = VaadinServlet.getCurrent().getServletContext().getContextPath();
                        final String redirectFragment = contextRoot + "/element/node.jsp?quiet=true&node=" + nodeId;

                        LOG.debug("node {} clicked, current location = {}, uri = {}", nodeId, currentLocation, redirectFragment);

                        try {
                            SurveillanceViewNotificationTable.this.getUI().addWindow(new InfoWindow(new URL(currentLocation.toURL(), redirectFragment), new InfoWindow.LabelCreator() {
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

                HorizontalLayout horizontalLayout = new HorizontalLayout();

                horizontalLayout.addComponent(icon);
                horizontalLayout.addComponent(button);

                horizontalLayout.setSpacing(true);

                return horizontalLayout;
            }
        });

        /**
         * set the cell style generator
         */
        setCellStyleGenerator(new CellStyleGenerator() {
            @Override
            public String getStyle(final Table source, final Object itemId, final Object propertyId) {
                OnmsNotification onmsNotification = ((OnmsNotification) itemId);
                return m_customSeverity.get(onmsNotification).toLowerCase();
            }
        });

        /**
         * set column headers
         */
        setColumnHeader("node", "Node");
        setColumnHeader("serviceType", "Service");
        setColumnHeader("textMsg", "Message");
        setColumnHeader("pageTime", "Sent Time");
        setColumnHeader("answeredBy", "Responder");
        setColumnHeader("respondTime", "Respond Time");

        /**
         * define visible columns
         */
        setVisibleColumns("node", "serviceType", "textMsg", "pageTime", "answeredBy", "respondTime");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshDetails(Set<OnmsCategory> rowCategories, Set<OnmsCategory> colCategories) {
        /**
         * retrieve the matching notifications
         */
        List<OnmsNotification> notifications = getSurveillanceViewService().getNotificationsForCategories(rowCategories, colCategories, m_customSeverity);

        /**
         * empty the bean container
         */
        m_beanItemContainer.removeAllItems();

        /**
         * add items to container
         */
        if (notifications != null && !notifications.isEmpty()) {
            for (OnmsNotification onmsNotification : notifications) {
                m_beanItemContainer.addItem(onmsNotification);
            }
        }

        /**
         * sort the items
         */
        sort(new Object[]{"pageTime"}, new boolean[]{false});

        /**
         * refresh the table
         */
        refreshRowCache();
    }
}
