/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.topology.app.internal.jung;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections15.Transformer;
import org.opennms.features.topology.api.GraphContainer;
import org.opennms.features.topology.app.internal.SimpleGraphContainer;
import org.opennms.features.topology.app.internal.TopoEdge;
import org.opennms.features.topology.app.internal.TopoGraph;

import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.graph.SparseGraph;

public class CircleLayoutAlgorithm extends AbstractLayoutAlgorithm {

	public void updateLayout(final GraphContainer graphContainer) {
		
		TopoGraph g = getGraph((SimpleGraphContainer) graphContainer);
		
		int szl = g.getSemanticZoomLevel();
		
		
		SparseGraph<Object, TopoEdge> jungGraph = new SparseGraph<Object, TopoEdge>();

		Collection<Object> vertices = g.getGraphContainer().getDisplayVertexIds(szl);
		
		for(Object v : vertices) {
			jungGraph.addVertex(v);
		}
		
		List<TopoEdge> edges = g.getEdges(szl);
		
		for(TopoEdge e : edges) {
			jungGraph.addEdge(e, e.getSource().getItemId(), e.getTarget().getItemId());
		}
		

		CircleLayout<Object, TopoEdge> layout = new CircleLayout<Object, TopoEdge>(jungGraph);
		layout.setInitializer(new Transformer<Object, Point2D>() {
			public Point2D transform(Object v) {
				return new Point(graphContainer.getX(v), graphContainer.getY(v));
			}
		});
		layout.setSize(selectLayoutSize(graphContainer));
		
		for(Object v : vertices) {
			graphContainer.setX(v, (int)layout.getX(v));
			graphContainer.setY(v, (int)layout.getY(v));
		}
		
		
		
		
	}

	private TopoGraph getGraph(final SimpleGraphContainer graphContainer) {
		return graphContainer.getGraph();
	}

	@Override
	protected Dimension selectLayoutSize(GraphContainer g) {
		int vertexCount = g.getDisplayVertexIds(g.getSemanticZoomLevel()).size();
		
		int spacing = ELBOW_ROOM/5;

		int diameter = (int)(vertexCount*spacing/Math.PI);

		 return new Dimension(diameter+ELBOW_ROOM, diameter+ELBOW_ROOM);

	}
	
	

}
