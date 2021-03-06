/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
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

package org.opennms.features.topology.app.internal.menu;

import java.util.List;
import java.util.Objects;

import org.opennms.features.topology.api.OperationContext;
import org.opennms.features.topology.api.support.breadcrumbs.Breadcrumb;
import org.opennms.features.topology.api.support.breadcrumbs.BreadcrumbCriteria;
import org.opennms.features.topology.api.topo.Criteria;
import org.opennms.features.topology.api.topo.GraphProvider;
import org.opennms.features.topology.api.topo.MetaTopologyProvider;
import org.opennms.features.topology.api.topo.VertexRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menu item to allow navigation to target vertices from a specific source vertex.
 */
public class NavigationMenuItem extends AbstractMenuItem {

    private static final Logger LOG = LoggerFactory.getLogger(NavigationMenuItem.class);
    private final GraphProvider targetGraphProvider;
    private final VertexRef sourceVertex;

    public NavigationMenuItem(GraphProvider targetGraphProvider, VertexRef sourceVertex) {
        this.targetGraphProvider = Objects.requireNonNull(targetGraphProvider);
        this.sourceVertex = Objects.requireNonNull(sourceVertex);
        setLabel(String.format("%s (%s)", targetGraphProvider.getTopologyProviderInfo().getName(), sourceVertex.getLabel()));
    }

    @Override
    public MenuCommand getCommand() {
        return (targets, operationContext) -> {
            Breadcrumb breadcrumb = new Breadcrumb(targetGraphProvider.getVertexNamespace(), sourceVertex);
            BreadcrumbCriteria criteria = Criteria.getSingleCriteriaForGraphContainer(operationContext.getGraphContainer(), BreadcrumbCriteria.class, true);
            criteria.setNewRoot(breadcrumb);
            criteria.handleClick(breadcrumb, operationContext.getGraphContainer());
        };
    }

    @Override
    public boolean isChecked(List<VertexRef> targets, OperationContext operationContext) {
        return false;
    }

    @Override
    public boolean isVisible(List<VertexRef> targets, OperationContext operationContext) {
        // Only display the operation, when we have a single vertex selected, and the topology contains multiple graphs
        final MetaTopologyProvider metaTopologyProvider = operationContext.getGraphContainer().getMetaTopologyProvider();
        return targets.size() == 1 && metaTopologyProvider.getGraphProviders().size() > 1;
    }

    @Override
    public boolean isEnabled(List<VertexRef> targets, OperationContext operationContext) {
        // Only enable the operation the vertex links to other graphs
        final MetaTopologyProvider metaTopologyProvider = operationContext.getGraphContainer().getMetaTopologyProvider();
        return targets.stream().findFirst()
                .map(v -> metaTopologyProvider.getOppositeVertices(v).size() > 0)
                .orElse(false);
    }
}
