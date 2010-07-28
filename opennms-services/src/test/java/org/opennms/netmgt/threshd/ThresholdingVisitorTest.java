//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2008 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.                                                            
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//       
// For more information contact: 
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//

package org.opennms.netmgt.threshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opennms.core.resource.Vault;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.collectd.AliasedResource;
import org.opennms.netmgt.collectd.AttributeGroupType;
import org.opennms.netmgt.collectd.CollectionAgent;
import org.opennms.netmgt.collectd.GenericIndexResource;
import org.opennms.netmgt.collectd.GenericIndexResourceType;
import org.opennms.netmgt.collectd.IfInfo;
import org.opennms.netmgt.collectd.IfResourceType;
import org.opennms.netmgt.collectd.NodeInfo;
import org.opennms.netmgt.collectd.NodeResourceType;
import org.opennms.netmgt.collectd.NumericAttributeType;
import org.opennms.netmgt.collectd.OnmsSnmpCollection;
import org.opennms.netmgt.collectd.ResourceType;
import org.opennms.netmgt.collectd.ServiceParameters;
import org.opennms.netmgt.collectd.SnmpAttributeType;
import org.opennms.netmgt.collectd.SnmpCollectionResource;
import org.opennms.netmgt.collectd.SnmpIfData;
import org.opennms.netmgt.config.DataSourceFactory;
import org.opennms.netmgt.config.DatabaseSchemaConfigFactory;
import org.opennms.netmgt.config.MibObject;
import org.opennms.netmgt.config.ThreshdConfigFactory;
import org.opennms.netmgt.config.ThreshdConfigManager;
import org.opennms.netmgt.config.ThresholdingConfigFactory;
import org.opennms.netmgt.dao.FilterDao;
import org.opennms.netmgt.dao.support.JdbcFilterDao;
import org.opennms.netmgt.dao.support.ResourceTypeUtils;
import org.opennms.netmgt.eventd.EventIpcManager;
import org.opennms.netmgt.eventd.EventIpcManagerFactory;
import org.opennms.netmgt.filter.FilterDaoFactory;
import org.opennms.netmgt.mock.EventAnticipator;
import org.opennms.netmgt.mock.MockDataCollectionConfig;
import org.opennms.netmgt.mock.MockDatabase;
import org.opennms.netmgt.mock.MockEventIpcManager;
import org.opennms.netmgt.mock.MockNetwork;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.RrdRepository;
import org.opennms.netmgt.snmp.SnmpInstId;
import org.opennms.netmgt.snmp.SnmpUtils;
import org.opennms.netmgt.snmp.SnmpValue;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.netmgt.xml.event.Parms;
import org.opennms.netmgt.xml.event.Value;
import org.opennms.test.mock.MockLogAppender;

/**
 * @author <a href="mailto:agalue@opennms.org">Alejandro Galue</a>
 *
 */
public class ThresholdingVisitorTest {

    Level m_defaultErrorLevelToCheck;
    FilterDao m_filterDao;
    EventAnticipator m_anticipator;
    List<Event> m_anticipatedEvents;
    private Comparator<Parm> m_parmComparator;
    private Comparator<Event> m_eventComparator;
    
    @Before
    public void setUp() throws Exception {
        // Resets Counters Cache Data
        System.setProperty("org.opennms.thresholds.filtersReloadEnabled", "false");
        CollectionResourceWrapper.s_cache.clear();

        // This is set at ERROR because JEXL prints some harmless, expected warning messages
        m_defaultErrorLevelToCheck = Level.ERROR;
        System.setProperty("mock.logLevel", "DEBUG");
        MockLogAppender.setupLogging();

        m_filterDao = EasyMock.createMock(FilterDao.class);
        EasyMock.expect(m_filterDao.getIPList((String)EasyMock.anyObject())).andReturn(Collections.singletonList("127.0.0.1")).anyTimes();
        FilterDaoFactory.setInstance(m_filterDao);
        EasyMock.replay(m_filterDao);

        m_anticipator = new EventAnticipator();
        MockEventIpcManager eventMgr = new MockEventIpcManager();
        eventMgr.setEventAnticipator(m_anticipator);
        eventMgr.setSynchronous(true);
        EventIpcManager eventdIpcMgr = (EventIpcManager)eventMgr;
        EventIpcManagerFactory.setIpcManager(eventdIpcMgr);
        
        initFactories("/threshd-configuration.xml","/test-thresholds.xml");
        m_anticipatedEvents = new ArrayList<Event>();
        
        m_parmComparator = new Comparator<Parm>() {

            public int compare(Parm o1, Parm o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null && o2 != null) return 1;
                if (o1 != null && o2 == null) return -1;

                int retVal = o1.getParmName().compareTo(o2.getParmName());
                if (retVal == 0) {
                    String c1 = o1.getValue().getContent();
                    String c2 = o2.getValue().getContent();
                    if (c1 == null && c2 == null) return 0;
                    if (c1 == null && c2 != null) return 1;
                    if (c1 != null && c2 == null) return -1;
                    
                    retVal = c1.compareTo(c2);
                }
                return retVal;
            }
        };

        m_eventComparator = new Comparator<Event>() {

            private int compareStrings(String s1, String s2) {
                if (s1 == null && s2 == null) return 0;
                if (s1 == null && s2 != null) return 1;
                if (s1 != null && s2 == null) return -1;
                return (s1.compareTo(s2));
            }

            public int compare(Event e1, Event e2) {
                if (e1 == null && e2 == null) return 0;
                if (e1 == null && e2 != null) return 1;
                if (e1 != null && e2 == null) return -1;

                int retVal = compareStrings(e1.getUei(), e2.getUei());
                if (retVal == 0) {
                    retVal = compareStrings(e1.getInterface(), e2.getInterface());
                }
                if (retVal == 0) {
                    retVal = compareStrings(e1.getService(), e2.getService());
                }
                if (retVal == 0) {
                    List<Parm> anticipatedParms = e1.getParms().getParmCollection();
                    List<Parm> receivedParms = e2.getParms().getParmCollection();
                    Collections.sort(anticipatedParms, m_parmComparator);
                    Collections.sort(receivedParms, m_parmComparator);
                    if (anticipatedParms.size() != receivedParms.size()) {
                        retVal = Integer.valueOf(anticipatedParms.size()).compareTo(Integer.valueOf(receivedParms.size()));
                    }
                    if (retVal == 0) {
                        for (int i = 0; i < anticipatedParms.size(); i++) {
                            Parm anticipated = anticipatedParms.get(i);
                            Parm received = receivedParms.get(i);
                            
                            retVal = compareStrings(anticipated.getParmName(), received.getParmName());
                            if (retVal == 0) {
                                retVal = compareStrings(anticipated.getValue().getContent(), received.getValue().getContent());
                            }
                            if (retVal != 0) {
                                break;
                            }
                        }
                    }
                }
                
                return retVal;
            }
        };
    }

    private void initFactories(String threshd, String thresholds) throws Exception {
        log().info("Initialize Threshold Factories");
        ThresholdingConfigFactory.setInstance(new ThresholdingConfigFactory(getClass().getResourceAsStream(thresholds)));
        ThreshdConfigFactory.setInstance(new ThreshdConfigFactory(getClass().getResourceAsStream(threshd),"127.0.0.1", false));
    }
    
    @After
    public void tearDown() throws Exception {
        MockLogAppender.assertNotGreaterOrEqual(m_defaultErrorLevelToCheck);
        EasyMock.verify(m_filterDao);
    }

    @Test
    public void testCreateVisitor() {
        createVisitor();
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     */
    @Test
    public void testResourceGaugeData() {
        addHighThresholdEvent(1, 10000, 5000, 15000, "Unknown", null, "freeMem", null, null);
        ThresholdingVisitor visitor = createVisitor();
        runGaugeDataTest(visitor, 15000);
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * Updated to reflect the fact that counter are treated as rates (counter wrap is not checked here anymore).
     */
    @Test
    public void testResourceCounterData() throws Exception {
        initFactories("/threshd-configuration.xml", "/test-thresholds-counters.xml");
        ThresholdingVisitor visitor = createVisitor();

        CollectionAgent agent = createCollectionAgent();
        NodeResourceType resourceType = createNodeResourceType(agent);
        MibObject mibObject = createMibObject("counter", "myCounter", "0");
        SnmpAttributeType attributeType = new NumericAttributeType(resourceType, "default", mibObject, new AttributeGroupType("mibGroup", "ignore"));

        // Add Events
        addHighThresholdEvent(1, 10, 5, 15, "Unknown", null, "myCounter", null, null);
        addHighRearmEvent(1, 10, 5, 2, "Unknown", null, "myCounter", null, null);

        // Collect Step 1 : Initialize counter cache.
        SnmpCollectionResource resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(1000));
        resource.visit(visitor);

        // Collect Step 2 : Trigger. (last-current)/step => (5500-1000)/300=15
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(5500));
        resource.visit(visitor);

        // Collect Step 3 : Rearm. (last-current)/step => (6100-5500)/300=2
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(6100));
        resource.visit(visitor);

        EasyMock.verify(agent);
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testInterfaceResourceWithDBAttributeFilter() throws Exception {
        Integer ifIndex = 1;
        Long ifSpeed = 10000000l;
        String ifName = "wlan0";
        addHighThresholdEvent(1, 90, 50, 120, ifName, ifIndex.toString(), "ifOutOctets", ifName, ifIndex.toString());
        addHighThresholdEvent(1, 90, 50, 120, ifName, ifIndex.toString(), "ifInOctets", ifName, ifIndex.toString());
        runInterfaceResource(createVisitor(), "127.0.0.1", ifName, ifSpeed, ifIndex, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testInterfaceResourceWithStringAttributeFilter() throws Exception {
        Integer ifIndex = 1;
        Long ifSpeed = 10000000l;
        String ifName = "sis0";
        addHighThresholdEvent(1, 90, 50, 120, ifName, ifIndex.toString(), "ifOutOctets", ifName, ifIndex.toString());
        addHighThresholdEvent(1, 90, 50, 120, ifName, ifIndex.toString(), "ifInOctets", ifName, ifIndex.toString());

        File resourceDir = new File(getRepository().getRrdBaseDir(), "1/" + ifName);
        resourceDir.deleteOnExit();
        resourceDir.mkdirs();
        Properties p = new Properties();
        p.put("myMockParam", "myMockValue");
        ResourceTypeUtils.saveUpdatedProperties(new File(resourceDir, "strings.properties"), p);
        
        runInterfaceResource(createVisitor(), "127.0.0.1", ifName, ifSpeed, ifIndex, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        verifyEvents(0);
        deleteDirectory(new File(getRepository().getRrdBaseDir(), "1"));
    }
    
    /*
     * Before call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * After call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-2.xml
     */
    @Test
    public void testReloadConfiguration() throws Exception {
        ThresholdingVisitor visitor = createVisitor();
        
        // Step 1: No events
        addHighThresholdEvent(1, 10000, 5000, 4500, "Unknown", null, "freeMem", null, null);
        runGaugeDataTest(visitor, 4500);
        verifyEvents(1);
        
        // Step 2: Change configuration
        initFactories("/threshd-configuration.xml","/test-thresholds-2.xml");
        visitor.reload();
        resetAnticipator();
        
        // Step 3: Trigger threshold with new configuration values
        addHighThresholdEvent(1, 4000, 2000, 4500, "Unknown", null, "freeMem", null, null);
        runGaugeDataTest(visitor, 4500);
        verifyEvents(0);
    }

    /*
     * This bug has not been replicated, but this code covers the apparent scenario, and can be adapted to match
     * any scenario which can actually replicate the reported issue
     * 
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-bug2746.xml
     */
    @Test
    public void testBug2746() throws Exception{
        initFactories("/threshd-configuration.xml","/test-thresholds-bug2746.xml");

        ThresholdingVisitor visitor = createVisitor();

        CollectionAgent agent = createCollectionAgent();
        NodeResourceType resourceType = createNodeResourceType(agent);
        MibObject mibObject = createMibObject("gauge", "bug2746", "0");
        SnmpAttributeType attributeType = new NumericAttributeType(resourceType, "default", mibObject, new AttributeGroupType("mibGroup", "ignore"));

        // Add Events
        addHighThresholdEvent(1, 50, 40, 60, "Unknown", null, "bug2746", null, null);

        // Step 1 : Execute visitor
        SnmpCollectionResource resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getGauge32(20));
        resource.visit(visitor);
        
        // Step 2 : Repeat a couple of times with the same value, to replicate a steady state
        resource.visit(visitor);
        resource.visit(visitor);
        resource.visit(visitor);

        // Step 3 : Trigger
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getGauge32(60));
        resource.visit(visitor);

        // Step 4 : Don't rearm, but do drop
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getGauge32(45));
        resource.visit(visitor);

        // Step 5 : Shouldn't trigger again
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getGauge32(55));
        resource.visit(visitor);

        EasyMock.verify(agent);
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     */
    @Test
    public void testBug3146_unrelatedChange() throws Exception {
        ThresholdingVisitor visitor = createVisitor();
        
        // Add Events
        addHighThresholdEvent(1, 10000, 5000, 12000, "Unknown", null, "freeMem", null, null);
        addHighRearmEvent(1, 10000, 5000, 1000, "Unknown", null, "freeMem", null, null);
        
        // Step 1: Trigger threshold
        runGaugeDataTest(visitor, 12000);
        
        // Step 2: Reload Configuration (changes are not related to triggered threshold)
        visitor.reload();
        
        // Step 3: Send Rearmed event
        runGaugeDataTest(visitor, 1000);
        
        // Verify Events
        verifyEvents(0);
    }
    
    /*
     * Before call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * After call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-2.xml
     */
    @Test
    public void testBug3146_reduceTrigger() throws Exception {
        ThresholdingVisitor visitor = createVisitor();

        // Add Events
        addHighThresholdEvent(1, 10000, 5000, 12000, "Unknown", null, "freeMem", null, null);
        addHighRearmEvent(1, 10000, 5000, Double.NaN, "Unknown", null, "freeMem", null, null);
        addHighThresholdEvent(1, 4000, 2000, 5000, "Unknown", null, "freeMem", null, null);
        addHighRearmEvent(1, 4000, 2000, 1000, "Unknown", null, "freeMem", null, null);

        // Step 1: Trigger threshold
        runGaugeDataTest(visitor, 12000);
        
        // Step 2: Change Configuration (reducing value for already triggered threshold)
        initFactories("/threshd-configuration.xml","/test-thresholds-2.xml");

        // Step 3: Execute Merge Configuration
        visitor.reload();

        // Step 4: Trigger threshold (with new value)
        runGaugeDataTest(visitor, 5000);
        
        // Step 5: Send Rearmed event (with new value)
        runGaugeDataTest(visitor, 1000);
        
        // Verify Events
        verifyEvents(0);
    }

    /*
     * Before call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * After call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-3.xml
     */
    @Test
    public void testBug3146_inceaseTrigger() throws Exception {
        ThresholdingVisitor visitor = createVisitor();

        // Add Events
        addHighThresholdEvent(1, 10000, 5000, 12000, "Unknown", null, "freeMem", null, null);
        addHighRearmEvent(1, 10000, 5000, Double.NaN, "Unknown", null, "freeMem", null, null);

        // Step 1: Trigger threshold
        runGaugeDataTest(visitor, 12000);
        
        // Step 2: Change Configuration (increasing value for already triggered threshold)
        initFactories("/threshd-configuration.xml","/test-thresholds-3.xml");
        
        // Step 3: Execute Merge Configuration (Rearmed Event must be sent).
        visitor.reload();
        verifyEvents(0);
        
        // Step 4: New collected data is not above the new threshold value. No Events generated
        resetAnticipator();
        addHighThresholdEvent(1, 15000, 14000, 13000, "Unknown", null, "freeMem", null, null);
        runGaugeDataTest(visitor, 13000);
        verifyEvents(1);
        
        // Step 5: Trigger and rearm a threshold using new configuration
        resetAnticipator();
        addHighThresholdEvent(1, 15000, 14000, 16000, "Unknown", null, "freeMem", null, null);
        addHighRearmEvent(1, 15000, 14000, 1000, "Unknown", null, "freeMem", null, null);
        runGaugeDataTest(visitor, 16000);
        runGaugeDataTest(visitor, 1000);
        verifyEvents(0);
    }

    /*
     * If I have a high threshold triggered, and then replace it with their equivalent low threshold,
     * The high definition must be removed from cache and rearmed event must be sent.
     * 
     * Before call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * After call visitor.reload(), this test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-4.xml
     */
    @Test
    public void testBug3146_replaceThreshold() throws Exception {
        ThresholdingVisitor visitor = createVisitor();
        
        // Add Events
        String lowThresholdUei = "uei.opennms.org/threshold/lowThresholdExceeded";
        String highExpression = "(((hrStorageAllocUnits*hrStorageUsed)/(hrStorageAllocUnits*hrStorageSize))*100)";
        String lowExpression = "(100-((hrStorageAllocUnits*hrStorageUsed)/(hrStorageAllocUnits*hrStorageSize))*100)";
        addHighThresholdEvent(1, 30, 25, 50, "/opt", "1", highExpression, null, null);
        addHighRearmEvent(1, 30, 25, Double.NaN, "/opt", "1", highExpression, null, null);
        addEvent(lowThresholdUei, "127.0.0.1", "SNMP", 1, 10.0, 20.0, 5.0, "/opt", "1", lowExpression, null, null);

        // Step 1: Trigger threshold
        runFileSystemDataTest(visitor, 1, "/opt", 500, 1000);

        // Step 2: Reload Configuration (merge). Threshold definition was replaced.
        initFactories("/threshd-configuration.xml","/test-thresholds-4.xml");
        visitor.reload();
        
        // Step 3: Must trigger only one low threshold exceeded
        runFileSystemDataTest(visitor, 1, "/opt", 950, 1000);
        
        verifyEvents(0);
    }
    
    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-bug3193.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug3193() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3193.xml");
        ThresholdingVisitor visitor = createVisitor();

        CollectionAgent agent = createCollectionAgent();
        NodeResourceType resourceType = createNodeResourceType(agent);
        MibObject mibObject = createMibObject("counter", "myCounter", "0");
        SnmpAttributeType attributeType = new NumericAttributeType(resourceType, "default", mibObject, new AttributeGroupType("mibGroup", "ignore"));

        // Add Events
        addHighThresholdEvent(1, 100, 90, 110, "Unknown", null, "myCounter", null, null);
        addHighThresholdEvent(1, 70, 60, 80, "Unknown", null, "myCounter - 30", null, null);
        addHighRearmEvent(1, 100, 90, 40, "Unknown", null, "myCounter", null, null);
        addHighRearmEvent(1, 70, 60, 10, "Unknown", null, "myCounter - 30", null, null);            

        // Collect Step 1 : First Data: Last should be NaN
        SnmpCollectionResource resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(2000));
        resource.visit(visitor);

        // Collect Step 2 : First Value: (last-current)/step => (20000-2000)/300=60
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(20000));
        resource.visit(visitor);

        // Collect Step 3 : Second Value: (last-current)/step => (53000-20000)/300=110 => Trigger
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(53000));
        resource.visit(visitor);

        // Collect Step 3 : Third Value (last-current)/step => (65000-53000)/300=40 => Rearm
        resource = new NodeInfo(resourceType, agent);
        resource.setAttributeValue(attributeType, SnmpUtils.getValueFactory().getCounter32(65000));
        resource.visit(visitor);

        EasyMock.verify(agent);
        verifyEvents(0);
    }
    
    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-2.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug2711_noIpAddress() throws Exception {
        runTestForBug2711(2, 0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-2.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug2711_noIP_badIfIndex() throws Exception {
        runTestForBug2711(-100, 2);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-2.xml
     * 
     * There is no Frame Relay related thresholds definitions on test-thresholds-2.xml.
     * When visit resources, getEntityMap from ThresholdingSet must null.
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug3227() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3227.xml");
        ThresholdingVisitor visitor = createVisitor();
        CollectionAgent agent = createCollectionAgent();
        GenericIndexResourceType resourceType = createGenericIndexResourceType(agent, "frCircuitIfIndex");

        // Creating Resource
        SnmpInstId inst = new SnmpInstId(100);
        SnmpCollectionResource resource = new GenericIndexResource(resourceType, "frCircuitIfIndex", inst);
        addAttributeToCollectionResource(resource, resourceType, "frReceivedOctets", "counter", "frCircuitIfIndex", 1000);
        addAttributeToCollectionResource(resource, resourceType, "frSentOctets", "counter", "frCircuitIfIndex", 1000);
        
        /*
         * Run Visitor
         * I must receive 2 special info events because getEntityMap should be called 2 times.
         * One for each attribute and one for each resource.
         * Original code will throw a NullPointerException after call getEntityMap.
         * Original code expects WARNs, but this message is now an INFO.
         */
        resource.visit(visitor);
        LoggingEvent[] events = MockLogAppender.getEventsGreaterOrEqual(Level.INFO);
        int count = 0;
        String expectedMsg = "getEntityMap: No thresholds configured for resource type frCircuitIfIndex. Not processing this collection.";
        for (LoggingEvent e : events) {
            if (e.getMessage().equals(expectedMsg))
                count++;
        }
        assertEquals("expecting 2 events", 2, count);
    }

    /*
     * Testing 32-bit counter wrapping on ifOutOctets
     */
    @Test
    public void testBug3194_32bits() throws Exception {
        runCounterWrapTest(32, 200);
    }

    /*
     * Testing 64-bit counter wrapping on ifOutOctets
     */
    @Test
    public void testBug3194_64bits() throws Exception {
        runCounterWrapTest(64, 201.6);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-6.xml
     */
    @Test
    public void testBug3333() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3333.xml");
        ThresholdingVisitor visitor = createVisitor();
        String expression = "hrStorageSize-hrStorageUsed";

        // Trigger Low Threshold
        addEvent("uei.opennms.org/threshold/lowThresholdExceeded", "127.0.0.1", "SNMP", 1, 10.0, 15.0, 5.0, "/opt", "1", expression, null, null);
        runFileSystemDataTest(visitor, 1, "/opt", 95, 100);
        verifyEvents(0);

        // Rearm Low Threshold and Trigger High Threshold
        addEvent("uei.opennms.org/threshold/lowThresholdRearmed", "127.0.0.1", "SNMP", 1, 10.0, 15.0, 60.0, "/opt", "1", expression, null, null);
        addHighThresholdEvent(1, 50, 45, 60, "/opt", "1", expression, null, null);
        runFileSystemDataTest(visitor, 1, "/opt", 40, 100);
        verifyEvents(0);
    }
    
    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3390.xml
     * - test-thresholds-bug3390.xml
     * 
     * The idea is to define many threshold-group parameters on a service inside a package
     */
    @Test
    public void testBug3390() throws Exception {
        initFactories("/threshd-configuration-bug3390.xml","/test-thresholds-bug3390.xml");
        
        // Validating threshd-configuration.xml
        ThreshdConfigManager configManager = ThreshdConfigFactory.getInstance();
        assertEquals(1, configManager.getConfiguration().getPackageCount());
        org.opennms.netmgt.config.threshd.Package pkg = configManager.getConfiguration().getPackage(0);
        assertEquals(1, pkg.getServiceCount());
        org.opennms.netmgt.config.threshd.Service svc = pkg.getService(0);
        assertEquals(5, svc.getParameterCount());
        int count = 0;
        for (org.opennms.netmgt.config.threshd.Parameter parameter : svc.getParameter()) {
            if (parameter.getKey().equals("thresholding-group"))
                count++;
        }
        assertEquals(5, count);

        // Validating Thresholding Set
        ThresholdingVisitor visitor = createVisitor();
        assertEquals(5, visitor.m_thresholdingSet.m_thresholdGroups.size());
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3554.xml
     * - test-thresholds-bug3554.xml
     */
    @Test
    public void testBug3554_withMockFilterDao() throws Exception {
        initFactories("/threshd-configuration-bug3554.xml","/test-thresholds-bug3554.xml");
        
        // Visitor with Mock FilterDao
        ThresholdingVisitor visitor = createVisitor();
        
        // Do nothing, just to check visitor
        runInterfaceResource(visitor, "127.0.0.1", "eth0", 10000000l, 1, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        
        // Do nothing, just to check visitor
        runGaugeDataTest(visitor, 12000);
        
        // Do nothing, just to check visitor
        CollectionAgent agent = createCollectionAgent();
        GenericIndexResourceType resourceType = createGenericIndexResourceType(agent, "ciscoEnvMonTemperatureStatusIndex");
        SnmpCollectionResource resource = new GenericIndexResource(resourceType, "ciscoEnvMonTemperatureStatusIndex", new SnmpInstId(45));
        resource.visit(visitor);
        EasyMock.verify(agent);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3554.xml
     * - test-thresholds-bug3554.xml
     * 
     * The problem is that every time we create a ThresholdingVisitor instance, the method
     * ThreshdConfigFactory.interfaceInPackage is called. This methods uses JdbcFilterDao
     * to evaluate node filter.
     * 
     * This filter evaluation is the reason of why collectd take too much to initialize on
     * large networks when in-line thresholding is enabled.
     * 
     * From test log, you can see that JdbcFilterDao is invoked on each visitor creation
     * iteration.
     */
    @Test
    public void testBug3554_withDBFilterDao() throws Exception {
        runTestForBug3554();

        // Validate FilterDao Calls
        int numOfPackages = ThreshdConfigFactory.getInstance().getConfiguration().getPackage().length;
        LoggingEvent[] events = MockLogAppender.getEventsGreaterOrEqual(Level.DEBUG);
        int count = 0;
        String expectedMsgHeader = "createPackageIpMap: package ";
        for (LoggingEvent e : events) {
            if (e.getMessage().toString().startsWith(expectedMsgHeader))
                count++;
        }
        assertEquals("expecting " + numOfPackages + " events", numOfPackages, count);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3554.xml
     * - test-thresholds-bug3554.xml
     * 
     * This test demonstrate that we can force filter auto-reload.
     */
    @Test
    public void testBug3720() throws Exception {
        System.setProperty("org.opennms.thresholds.filtersReloadEnabled", "true");
        runTestForBug3554();
        
        // Validate FilterDao Calls
        int numOfPackages = ThreshdConfigFactory.getInstance().getConfiguration().getPackage().length;
        int expectedCalls = numOfPackages * 26; // Not sure why is 5^2+1
        LoggingEvent[] events = MockLogAppender.getEventsGreaterOrEqual(Level.DEBUG);
        int count = 0;
        String expectedMsgHeader = "createPackageIpMap: package ";
        for (LoggingEvent e : events) {
            if (e.getMessage().toString().startsWith(expectedMsgHeader))
                count++;
        }
        assertEquals("expecting " + expectedCalls + " events", expectedCalls, count);
        
        // Validate number of re-initializations
        expectedCalls = 25; // 5 nodes => 5^2 times
        events = MockLogAppender.getEventsGreaterOrEqual(Level.INFO);
        count = 0;
        expectedMsgHeader = "getThresholdGroupNames: re-initializing filters.";
        for (LoggingEvent e : events) {
            if (e.getMessage().toString().equals(expectedMsgHeader))
                count++;
        }
        assertEquals("expecting " + expectedCalls + " events", expectedCalls, count);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3748.xml
     * - test-thresholds-bug3748.xml
     * 
     * This test has been created to validate absolute thresholds.
     */
    @Test
    public void testBug3748() throws Exception {
        initFactories("/threshd-configuration-bug3748.xml","/test-thresholds-bug3748.xml");
        // Absolute threshold evaluator doesn't show threshold and rearm levels on the event.
        addEvent("uei.opennms.org/threshold/absoluteChangeExceeded", "127.0.0.1", "SNMP", 1, null, null, 6.0, "Unknown", null, "freeMem", null, null);
        ThresholdingVisitor visitor = createVisitor();
        runGaugeDataTest(visitor, 2); // Set initial value
        runGaugeDataTest(visitor, 6); // Increment the value above configured threshold level: 6 - lastValue > 3, where lastValue=2
        verifyEvents(0);
    }
    
    // Execute an interface test where the physical interface doesn't have any IPAddress (i.e. ipAddr='0.0.0.0')
    // The event will always be associated to Agent Interface (see Bug 3808)
    private void runTestForBug2711(Integer ifIndex, Integer remainingEvents) throws Exception {
        Long ifSpeed = 10000000l;
        String ifName = "wlan0";
        initFactories("/threshd-configuration.xml","/test-thresholds-2.xml");
        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "SNMP", 1, 90.0, 50.0, 120.0, ifName, ifIndex.toString(), "ifOutOctets", ifName, ifIndex.toString());
        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "SNMP", 1, 90.0, 50.0, 120.0, ifName, ifIndex.toString(), "ifInOctets", ifName, ifIndex.toString());
        runInterfaceResource(createVisitor(), "0.0.0.0", ifName, ifSpeed, ifIndex, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        verifyEvents(remainingEvents);
    }

    private void runTestForBug3554() throws Exception {
        MockLogAppender.resetEvents();
        System.err.println("----------------------------------------------------------------------------------- begin test");

        String baseIpAddress = "10.0.0.";
        int numOfNodes = 5;

        // Initialize Mock Network

        MockNetwork network = new MockNetwork();
        network.setCriticalService("ICMP");

        for (int i=1; i<=numOfNodes; i++) {
            String ipAddress = baseIpAddress + i;
            network.addNode(i, "testNode-" + ipAddress);
            network.addInterface(ipAddress);
            network.setIfAlias("eth0");
            network.addService("ICMP");
            network.addService("SNMP");
        }

        MockDatabase db = new MockDatabase();
        db.populate(network);
        db.update("insert into categories (categoryid, categoryname) values (?, ?)", 10, "IPRA");
        db.update("insert into categories (categoryid, categoryname) values (?, ?)", 11, "NAS");
        for (int i=1; i<=numOfNodes; i++) {
            db.update("update snmpinterface set snmpifname=?, snmpifdescr=? where id=?", "eth0", "eth0", i);
            db.update("update node set nodesysoid=? where nodeid=?", ".1.3.6.1.4.1.9.1.222", i);
            db.update("insert into category_node values (?, ?)", 10, i);
            db.update("insert into category_node values (?, ?)", 11, i);
        }
        DataSourceFactory.setInstance(db);

        // Initialize Filter DAO

        System.setProperty("opennms.home", "src/test/resources");
        DatabaseSchemaConfigFactory.init();
        JdbcFilterDao jdbcFilterDao = new JdbcFilterDao();
        jdbcFilterDao.setDataSource(db);
        jdbcFilterDao.setDatabaseSchemaConfigFactory(DatabaseSchemaConfigFactory.getInstance());
        jdbcFilterDao.afterPropertiesSet();
        FilterDaoFactory.setInstance(jdbcFilterDao);

        // Initialize Factories

        initFactories("/threshd-configuration-bug3554.xml","/test-thresholds-bug3554.xml");

        // Initialize Thresholding Visitors

        Map<String,String> params = new HashMap<String,String>();
        params.put("thresholding-enabled", "true");

        for (int i=1; i<=numOfNodes; i++) {
            System.err.println("----------------------------------------------------------------------------------- visitor #" + i);
            String ipAddress = baseIpAddress + i;
            ThresholdingVisitor visitor = ThresholdingVisitor.create(1, ipAddress, "SNMP", getRepository(), params, 300000);
            assertNotNull(visitor);
            assertEquals(4, visitor.getThresholdGroups().size()); // mib2, cisco, ciscoIPRA, ciscoNAS
        }
        System.err.println("----------------------------------------------------------------------------------- end");
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3487.xml
     * - test-thresholds.xml
     */
    @Test
    public void testBug3487() throws Exception {
        initFactories("/threshd-configuration-bug3487.xml","/test-thresholds.xml");
        assertNotNull(createVisitor());
        m_defaultErrorLevelToCheck = Level.FATAL;
        LoggingEvent[] events = MockLogAppender.getEventsGreaterOrEqual(Level.ERROR);
        assertEquals("expecting 1 event", 1, events.length);
        assertEquals("initialize: Can't process threshold group SMS_Dieta", events[0].getMessage());
    }

    /*
     * Testing custom ThresholdingSet implementation for in-line Latency thresholds processing (Bug 3448)
     */
    @Test
    public void testBug3488() throws Exception {
        String ipAddress = "127.0.0.1";
        setupSnmpInterfaceDatabase(ipAddress, null);
        LatencyThresholdingSet thresholdingSet = new LatencyThresholdingSet(1, ipAddress, "HTTP", getRepository(), 0);
        assertTrue(thresholdingSet.hasThresholds()); // Global Test
        Map<String, Double> attributes = new HashMap<String, Double>();
        attributes.put("http", 200.0);
        assertTrue(thresholdingSet.hasThresholds(attributes)); // Datasource Test

        m_defaultErrorLevelToCheck = Level.ERROR;
        List<Event> triggerEvents = new ArrayList<Event>();
        for (int i=0; i<5; i++)
            triggerEvents.addAll(thresholdingSet.applyThresholds("http", attributes));
        LoggingEvent[] events = MockLogAppender.getEventsGreaterOrEqual(Level.WARN);
        assertEquals("expecting 5 events", 5, events.length);
        for (LoggingEvent e : events)
            assertEquals("Interface (nodeId/ipAddr=1/127.0.0.1) has no ifName and no ifDescr...setting to label to 'no_ifLabel'.", e.getMessage());
        assertTrue(triggerEvents.size() == 1);

        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "HTTP", 5, 100.0, 50.0, 200.0, "no_ifLabel", "127.0.0.1[http]", "http", "no_ifLabel", null);
        ThresholdingEventProxy proxy = new ThresholdingEventProxy();
        proxy.add(triggerEvents);
        proxy.sendAllEvents();
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration-bug3575.xml
     * - test-thresholds-bug3575.xml
     */
    @Test
    public void testBug3575() throws Exception {
        initFactories("/threshd-configuration-bug3575.xml","/test-thresholds-bug3575.xml");
        String ipAddress = "127.0.0.1";
        String ifName = "eth0";
        setupSnmpInterfaceDatabase(ipAddress, ifName);
        LatencyThresholdingSet thresholdingSet = new LatencyThresholdingSet(1, ipAddress, "StrafePing", getRepository(), 0);
        assertTrue(thresholdingSet.hasThresholds());
        Map<String, Double> attributes = new HashMap<String, Double>();
        for (double i=1; i<21; i++)
            attributes.put("ping" + i, 2 * i);
        attributes.put("loss", 60.0);
        attributes.put("response-time", 100.0);
        attributes.put("median", 100.0);
        assertTrue(thresholdingSet.hasThresholds(attributes));
        List<Event> triggerEvents = thresholdingSet.applyThresholds("StrafePing", attributes);
        assertTrue(triggerEvents.size() == 1);
        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "StrafePing", 1, 50.0, 25.0, 60.0, ifName, "127.0.0.1[StrafePing]", "loss", "eth0", null);
        ThresholdingEventProxy proxy = new ThresholdingEventProxy();
        proxy.add(triggerEvents);
        proxy.sendAllEvents();
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-bug3428.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug3428_noMatch() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3428.xml");
        Integer ifIndex = 1;
        Long ifSpeed = 10000000l; // 10Mbps - Bad Speed
        String ifName = "wlan0";
        addHighThresholdEvent(1, 90, 50, 120, "Unknown", ifIndex.toString(), "ifInOctets", ifName, ifIndex.toString());
        runInterfaceResource(createVisitor(), "127.0.0.1", ifName, ifSpeed, ifIndex, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        verifyEvents(1);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-bug3428.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug3428_match() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3428.xml");
        Integer ifIndex = 1;
        Long ifSpeed = 100000000l; // 100Mbps - Correct Speed!
        String ifName = "wlan0";
        addHighThresholdEvent(1, 90, 50, 120, ifName, ifIndex.toString(), "ifInOctets", ifName, ifIndex.toString());
        runInterfaceResource(createVisitor(), "127.0.0.1", ifName, ifSpeed, ifIndex, 10000, 46000); // real value = (46000 - 10000)/300 = 120
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-bug3664.xml
     * 
     * Updated to reflect the fact that counter are treated as rates.
     */
    @Test
    public void testBug3664() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-bug3664.xml");
        Integer ifIndex = 1;
        Long ifSpeed = 10000000l;
        String ifName = "wlan0";
        String domain = "myDomain";
        String ifAlias = ifName;
        String ifAliasComment = "#";

        String label = domain + "/" + ifAlias;
        addHighThresholdEvent(1, 90, 50, 120, label, null, "ifOutOctets", label, ifIndex.toString());
        addHighThresholdEvent(1, 90, 50, 120, label, null, "ifInOctets", label, ifIndex.toString());

        ThresholdingVisitor visitor = createVisitor();
        SnmpIfData ifData = createSnmpIfData("127.0.0.1", ifName, ifSpeed, ifIndex);
        CollectionAgent agent = createCollectionAgent();
        IfResourceType resourceType = createInterfaceResourceType(agent);

        // Step 1
        IfInfo ifInfo = new IfInfo(resourceType, agent, ifData);
        addAttributeToCollectionResource(ifInfo, resourceType, "ifInOctets", "counter", "ifIndex", 10000);
        addAttributeToCollectionResource(ifInfo, resourceType, "ifOutOctets", "counter", "ifIndex", 10000);
        AliasedResource resource = new AliasedResource(resourceType, domain, ifInfo, ifAliasComment, ifAlias);
        resource.visit(visitor);

        // Step 2 - Increment Counters
        ifInfo = new IfInfo(resourceType, agent, ifData);
        addAttributeToCollectionResource(ifInfo, resourceType, "ifInOctets", "counter", "ifIndex", 46000);
        addAttributeToCollectionResource(ifInfo, resourceType, "ifOutOctets", "counter", "ifIndex", 46000);
        resource = new AliasedResource(resourceType, domain, ifInfo, ifAliasComment, ifAlias);
        resource.visit(visitor);

        EasyMock.verify(agent);
        verifyEvents(0);
    }

    /*
     * Testing custom ThresholdingSet implementation for in-line Latency thresholds processing for Pollerd.
     * 
     * This test validate that Bug 1582 has been fixed.
     * ifLabel and ifIndex are set correctly based on Bug 2711
     */
    @Test    
    public void testLatencyThresholdingSet() throws Exception {
        Integer ifIndex = 1;
        String ifName = "lo0";
        setupSnmpInterfaceDatabase("127.0.0.1", ifName);

        LatencyThresholdingSet thresholdingSet = new LatencyThresholdingSet(1, "127.0.0.1", "HTTP", getRepository(), 0);
        assertTrue(thresholdingSet.hasThresholds()); // Global Test
        Map<String, Double> attributes = new HashMap<String, Double>();
        attributes.put("http", 90.0);
        assertTrue(thresholdingSet.hasThresholds(attributes)); // Datasource Test
        List<Event> triggerEvents = thresholdingSet.applyThresholds("http", attributes);
        assertTrue(triggerEvents.size() == 0);

        // Test Trigger
        attributes.put("http", 200.0);
        for (int i = 1; i < 5; i++) {
            log().debug("testLatencyThresholdingSet: run number " + i);
            if (thresholdingSet.hasThresholds(attributes)) {
                triggerEvents = thresholdingSet.applyThresholds("http", attributes);
                assertTrue(triggerEvents.size() == 0);
            }
        }
        if (thresholdingSet.hasThresholds(attributes)) {
            log().debug("testLatencyThresholdingSet: run number 5");
            triggerEvents = thresholdingSet.applyThresholds("http", attributes);
            assertTrue(triggerEvents.size() == 1);
        }
        
        // Test Rearm
        List<Event> rearmEvents = null;
        if (thresholdingSet.hasThresholds(attributes)) {
            attributes.put("http", 40.0);
            rearmEvents = thresholdingSet.applyThresholds("http", attributes);
            assertTrue(rearmEvents.size() == 1);
        }

        // Validate Events
        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "HTTP", 5, 100.0, 50.0, 200.0, ifName, "127.0.0.1[http]", "http", ifName, ifIndex.toString());
        addEvent("uei.opennms.org/threshold/highThresholdRearmed", "127.0.0.1", "HTTP", 5, 100.0, 50.0, 40.0, ifName, "127.0.0.1[http]", "http", ifName, ifIndex.toString());
        ThresholdingEventProxy proxy = new ThresholdingEventProxy();
        proxy.add(triggerEvents);
        proxy.add(rearmEvents);
        proxy.sendAllEvents();
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds.xml
     * 
     * It is important to add ".*" at the end of resource-filter tag definition in order to match many resources
     * like this test; for example:
     * 
     * <resource-filter field="hrStorageDescr">^/opt.*</resource-filter>
     * 
     * If we forgot it, /opt01 will not pass threshold filter
     */
    @Test
    public void testThresholsFiltersOnGenericResource() throws Exception {
        ThresholdingVisitor visitor = createVisitor();
        
        String highExpression = "(((hrStorageAllocUnits*hrStorageUsed)/(hrStorageAllocUnits*hrStorageSize))*100)";
        addHighThresholdEvent(1, 30, 25, 50, "/opt", "1", highExpression, null, null);
        addHighThresholdEvent(1, 30, 25, 60, "/opt01", "2", highExpression, null, null);

        runFileSystemDataTest(visitor, 1, "/opt", 50, 100);
        runFileSystemDataTest(visitor, 2, "/opt01", 60, 100);
        runFileSystemDataTest(visitor, 3, "/home", 70, 100);
        
        verifyEvents(0);
    }

    /*
     * This test uses this files from src/test/resources:
     * - thresd-configuration.xml
     * - test-thresholds-5.xml
     */
    @Test
    public void testThresholsFiltersOnNodeResource() throws Exception {
        initFactories("/threshd-configuration.xml","/test-thresholds-5.xml");
        ThresholdingVisitor visitor = createVisitor();
        
        // Adding Expected Thresholds
        addHighThresholdEvent(1, 30, 25, 50, "/home", null, "(hda1_hrStorageUsed/hda1_hrStorageSize)*100", null, null);
        addHighThresholdEvent(1, 50, 45, 60, "/opt", null, "(hda2_hrStorageUsed/hda2_hrStorageSize)*100", null, null);

        // Creating Node ResourceType
        CollectionAgent agent = createCollectionAgent();
        MockDataCollectionConfig dataCollectionConfig = new MockDataCollectionConfig();        
        OnmsSnmpCollection collection = new OnmsSnmpCollection(agent, new ServiceParameters(new HashMap<String, String>()), dataCollectionConfig);
        NodeResourceType resourceType = new NodeResourceType(agent, collection);

        // Creating strings.properties file
        Properties p = new Properties();
        p.put("hda1_hrStorageDescr", "/home");
        p.put("hda2_hrStorageDescr", "/opt");
        p.put("hda3_hrStorageDescr", "/usr");
        File f = new File(getRepository().getRrdBaseDir(), "1/strings.properties");
        ResourceTypeUtils.saveUpdatedProperties(f, p);
        
        // Creating Resource
        SnmpCollectionResource resource = new NodeInfo(resourceType, agent);
        addAttributeToCollectionResource(resource, resourceType, "hda1_hrStorageUsed", "gauge", "node", 50);
        addAttributeToCollectionResource(resource, resourceType, "hda1_hrStorageSize", "gauge", "node", 100);
        addAttributeToCollectionResource(resource, resourceType, "hda2_hrStorageUsed", "gauge", "node", 60);
        addAttributeToCollectionResource(resource, resourceType, "hda2_hrStorageSize", "gauge", "node", 100);
        addAttributeToCollectionResource(resource, resourceType, "hda3_hrStorageUsed", "gauge", "node", 70);
        addAttributeToCollectionResource(resource, resourceType, "hda3_hrStorageSize", "gauge", "node", 100);

        // Run Visitor and Verify Events
        resource.visit(visitor);
        EasyMock.verify(agent);
        f.delete();
        verifyEvents(0);
    }

    private ThresholdingVisitor createVisitor() {
        Map<String,String> params = new HashMap<String,String>();
        params.put("thresholding-enabled", "true");
        ThresholdingVisitor visitor = ThresholdingVisitor.create(1, "127.0.0.1", "SNMP", getRepository(), params, 300000);
        assertNotNull(visitor);
        return visitor;
    }

    private void runGaugeDataTest(ThresholdingVisitor visitor, long value) {
        CollectionAgent agent = createCollectionAgent();
        NodeResourceType resourceType = createNodeResourceType(agent);
        SnmpCollectionResource resource = new NodeInfo(resourceType, agent);
        addAttributeToCollectionResource(resource, resourceType, "freeMem", "gauge", "0", value);        
        resource.visit(visitor);
        EasyMock.verify(agent);
    }

    private void runInterfaceResource(ThresholdingVisitor visitor, String ipAddress, String ifName, Long ifSpeed, Integer ifIndex, long v1, long v2) {
        SnmpIfData ifData = createSnmpIfData(ipAddress, ifName, ifSpeed, ifIndex);
        CollectionAgent agent = createCollectionAgent();
        IfResourceType resourceType = createInterfaceResourceType(agent);

        // Step 1
        SnmpCollectionResource resource = new IfInfo(resourceType, agent, ifData);
        addAttributeToCollectionResource(resource, resourceType, "ifInOctets", "counter", "ifIndex", v1);
        addAttributeToCollectionResource(resource, resourceType, "ifOutOctets", "counter", "ifIndex", v1);
        resource.visit(visitor);
        
        // Step 2 - Increment Counters
        resource = new IfInfo(resourceType, agent, ifData);
        addAttributeToCollectionResource(resource, resourceType, "ifInOctets", "counter", "ifIndex", v2);
        addAttributeToCollectionResource(resource, resourceType, "ifOutOctets", "counter", "ifIndex", v2);
        resource.visit(visitor);

        EasyMock.verify(agent);
    }

    private void runFileSystemDataTest(ThresholdingVisitor visitor, int resourceId, String fs, long value, long max) throws Exception {
        CollectionAgent agent = createCollectionAgent();
        // Creating Generic ResourceType
        GenericIndexResourceType resourceType = createGenericIndexResourceType(agent, "hrStorageIndex");
        // Creating strings.properties file
        Properties p = new Properties();
        p.put("hrStorageType", ".1.3.6.1.2.1.25.2.1.4");
        p.put("hrStorageDescr", fs);
        File f = new File(getRepository().getRrdBaseDir(), "1/hrStorageIndex/" + resourceId + "/strings.properties");
        ResourceTypeUtils.saveUpdatedProperties(f, p);
        // Creating Resource
        SnmpInstId inst = new SnmpInstId(resourceId);
        SnmpCollectionResource resource = new GenericIndexResource(resourceType, "hrStorageIndex", inst);
        addAttributeToCollectionResource(resource, resourceType, "hrStorageUsed", "gauge", "hrStorageIndex", value);
        addAttributeToCollectionResource(resource, resourceType, "hrStorageSize", "gauge", "hrStorageIndex", max);
        addAttributeToCollectionResource(resource, resourceType, "hrStorageAllocUnits", "gauge", "hrStorageIndex", 1);
        // Run Visitor
        resource.visit(visitor);
        EasyMock.verify(agent);
        f.delete();
    }

    /*
     * Parameter expectedValue should be around 200:
     * Initial counter value is 20000 below limit.
     * Next value is 40000, so the difference will be 60000.
     * Counters are treated as rates so 60000/300 is 200.
     */
    private void runCounterWrapTest(double bits, double expectedValue) throws Exception {
        Integer ifIndex = 1;
        Long ifSpeed = 10000000l;
        String ifName = "wlan0";

        initFactories("/threshd-configuration.xml","/test-thresholds-bug3194.xml");
        addHighThresholdEvent(1, 100, 90, expectedValue, ifName, "1", "ifOutOctets", ifName, ifIndex.toString());
        ThresholdingVisitor visitor = createVisitor();
        
        // Creating Interface Resource Type
        SnmpIfData ifData = createSnmpIfData("127.0.0.1", ifName, ifSpeed, ifIndex);
        CollectionAgent agent = createCollectionAgent();
        IfResourceType resourceType = createInterfaceResourceType(agent);

        // Creating Data Source
        MibObject object = createMibObject("counter", "ifOutOctets", "ifIndex");
        SnmpAttributeType objectType = new NumericAttributeType(resourceType, "default", object, new AttributeGroupType("mibGroup", "ignore"));

        // Step 1 - Initialize Counter
        BigDecimal n = new BigDecimal(Math.pow(2, bits) - 20000);
        SnmpValue snmpValue1 = SnmpUtils.getValueFactory().getCounter64(n.toBigInteger());
        SnmpCollectionResource resource1 = new IfInfo(resourceType, agent, ifData);
        resource1.setAttributeValue(objectType, snmpValue1);
        resource1.visit(visitor);
        
        // Step 2 - Wrap Counter
        SnmpValue snmpValue2 = SnmpUtils.getValueFactory().getCounter64(new BigInteger("40000"));
        SnmpCollectionResource resource2 = new IfInfo(resourceType, agent, ifData);
        resource2.setAttributeValue(objectType, snmpValue2);
        resource2.visit(visitor);

        // Verify Events
        EasyMock.verify(agent);        
        verifyEvents(0);
    }
    
    private CollectionAgent createCollectionAgent() {
        CollectionAgent agent = EasyMock.createMock(CollectionAgent.class);
        EasyMock.expect(agent.getNodeId()).andReturn(1).anyTimes();
        EasyMock.expect(agent.getHostAddress()).andReturn("127.0.0.1").anyTimes();
        EasyMock.expect(agent.getSnmpInterfaceInfo((IfResourceType)EasyMock.anyObject())).andReturn(new HashSet<IfInfo>()).anyTimes();
        EasyMock.replay(agent);
        return agent;
    }

    private NodeResourceType createNodeResourceType(CollectionAgent agent) {
        MockDataCollectionConfig dataCollectionConfig = new MockDataCollectionConfig();        
        OnmsSnmpCollection collection = new OnmsSnmpCollection(agent, new ServiceParameters(new HashMap<String, String>()), dataCollectionConfig);
        return new NodeResourceType(agent, collection);
    }

    private IfResourceType createInterfaceResourceType(CollectionAgent agent) {
        MockDataCollectionConfig dataCollectionConfig = new MockDataCollectionConfig();        
        OnmsSnmpCollection collection = new OnmsSnmpCollection(agent, new ServiceParameters(new HashMap<String, String>()), dataCollectionConfig);
        return new IfResourceType(agent, collection);
    }

    private GenericIndexResourceType createGenericIndexResourceType(CollectionAgent agent, String resourceTypeName) {
        org.opennms.netmgt.config.datacollection.ResourceType type = new org.opennms.netmgt.config.datacollection.ResourceType();
        type.setName(resourceTypeName);
        type.setLabel(resourceTypeName);
        org.opennms.netmgt.config.datacollection.StorageStrategy strategy = new org.opennms.netmgt.config.datacollection.StorageStrategy();
        strategy.setClazz("org.opennms.netmgt.dao.support.IndexStorageStrategy");
        type.setStorageStrategy(strategy);
        org.opennms.netmgt.config.datacollection.PersistenceSelectorStrategy pstrategy = new org.opennms.netmgt.config.datacollection.PersistenceSelectorStrategy();
        pstrategy.setClazz("org.opennms.netmgt.collectd.PersistAllSelectorStrategy");
        type.setPersistenceSelectorStrategy(pstrategy);
        MockDataCollectionConfig dataCollectionConfig = new MockDataCollectionConfig();
        OnmsSnmpCollection collection = new OnmsSnmpCollection(agent, new ServiceParameters(new HashMap<String, String>()), dataCollectionConfig);
        return new GenericIndexResourceType(agent, collection, type);
    }

    private void addAttributeToCollectionResource(SnmpCollectionResource resource, ResourceType type, String attributeName, String attributeType, String attributeInstance, long value) {
        MibObject object = createMibObject(attributeType, attributeName, attributeInstance);
        SnmpAttributeType objectType = new NumericAttributeType(type, "default", object, new AttributeGroupType("mibGroup", "ignore"));
        SnmpValue snmpValue = attributeType.equals("counter") ? SnmpUtils.getValueFactory().getCounter32(value) : SnmpUtils.getValueFactory().getGauge32(value);
        resource.setAttributeValue(objectType, snmpValue);
    }

    private MibObject createMibObject(String type, String alias, String instance) {
        MibObject mibObject = new MibObject();
        mibObject.setOid(".1.1.1.1");
        mibObject.setAlias(alias);
        mibObject.setType(type);
        mibObject.setInstance(instance);
        mibObject.setMaxval(null);
        mibObject.setMinval(null);
        return mibObject;
    }

    private RrdRepository getRepository() {
        RrdRepository repo = new RrdRepository();
        repo.setRrdBaseDir(new File("/tmp"));
        return repo;		
    }

    private void addHighThresholdEvent(int trigger, double threshold, double rearm, double value, String label, String instance, String ds, String ifLabel, String ifIndex) {
        addEvent("uei.opennms.org/threshold/highThresholdExceeded", "127.0.0.1", "SNMP", trigger, threshold, rearm, value, label, instance, ds, ifLabel, ifIndex);
    }

    private void addHighRearmEvent(int trigger, double threshold, double rearm, double value, String label, String instance, String ds, String ifLabel, String ifIndex) {
        addEvent("uei.opennms.org/threshold/highThresholdRearmed", "127.0.0.1", "SNMP", trigger, threshold, rearm, value, label, instance, ds, ifLabel, ifIndex);
    }

    private void addEvent(String uei, String ipaddr, String service, Integer trigger, Double threshold, Double rearm, Double value, String label, String instance, String ds, String ifLabel, String ifIndex) {
        Event e = new Event();
        e.setUei(uei);
        e.setNodeid(1);
        e.setInterface(ipaddr);
        e.setService(service);
        Parms parms = new Parms();

        Parm p = new Parm();
        p.setParmName("label");
        Value v = new Value();
        v.setContent(label);
        p.setValue(v);
        parms.addParm(p);

        if (ifLabel != null) {
            p = new Parm();
            p.setParmName("ifLabel");
            v = new Value();
            v.setContent(ifLabel);
            p.setValue(v);
            parms.addParm(p);            
        }
        
        if (ifIndex != null) {
            p = new Parm();
            p.setParmName("ifIndex");
            v = new Value();
            v.setContent(ifIndex);
            p.setValue(v);
            parms.addParm(p);   
        }
        
        p = new Parm();
        p.setParmName("ds");
        v = new Value();
        v.setContent(ds);
        p.setValue(v);
        parms.addParm(p);
        
        if (value != null) {
            p = new Parm();
            p.setParmName("value");
            v = new Value();
            String pattern = System.getProperty("org.opennms.threshd.value.decimalformat", "###.##"); // See Bug 3427
            DecimalFormat valueFormatter = new DecimalFormat(pattern);
            v.setContent(valueFormatter.format(value));
            p.setValue(v);
            parms.addParm(p);
        }

        p = new Parm();
        p.setParmName("instance");
        v = new Value();
        v.setContent(instance);
        p.setValue(v);
        parms.addParm(p);

        p = new Parm();
        p.setParmName("trigger");
        v = new Value();
        v.setContent(Integer.toString(trigger));
        p.setValue(v);
        parms.addParm(p);

        if (threshold != null) {
            p = new Parm();
            p.setParmName("threshold");
            v = new Value();
            v.setContent(Double.toString(threshold));
            p.setValue(v);
            parms.addParm(p);
        }

        if (rearm != null) {
            p = new Parm();
            p.setParmName("rearm");
            v = new Value();
            v.setContent(Double.toString(rearm));
            p.setValue(v);
            parms.addParm(p);
        }

        e.setParms(parms);
        m_anticipator.anticipateEvent(e, true);
        m_anticipatedEvents.add(e);
    }

    private void verifyEvents(int remainEvents) {
        if (remainEvents == 0) {
            List<Event> receivedList = m_anticipator.getAnticipatedEventsRecieved();
            
            Collections.sort(receivedList, m_eventComparator);
            Collections.sort(m_anticipatedEvents, m_eventComparator);
            log().info("verifyEvents: Anticipated=" + m_anticipatedEvents.size() + ", Received=" + receivedList.size());
            if (m_anticipatedEvents.size() != receivedList.size()) {
                for (Event e : m_anticipatedEvents) {
                    System.err.println("expected event " + e.getUei() + ": " + e.getDescr());
                }
                System.err.println("anticipated = " + m_anticipatedEvents + ", received = " + receivedList);
                fail("Anticipated event count (" + m_anticipatedEvents.size() + ") is different from received event count (" + receivedList.size() + ").");
            }
            for (int i = 0; i < m_anticipatedEvents.size(); i++) {
                log().info("verifyEvents: processing event " + (i+1));
                compareEvents(m_anticipatedEvents.get(i), receivedList.get(i));
            }
        }
        m_anticipator.verifyAnticipated(0, 0, 0, remainEvents, 0);
    }
    
    private void compareEvents(Event anticipated, Event received) {
        assertEquals("UEIs must match", anticipated.getUei(), received.getUei());
        assertEquals("NodeIDs must match", anticipated.getNodeid(), received.getNodeid());
        assertEquals("interfaces must match", anticipated.getInterface(), received.getInterface());
        assertEquals("services must match", anticipated.getService(), received.getService());
        compareParms(anticipated.getParms().getParmCollection(), received.getParms().getParmCollection());
    }

    private void compareParms(List<Parm> anticipatedParms, List<Parm> receivedParms) {
        Collections.sort(anticipatedParms, m_parmComparator);
        Collections.sort(receivedParms, m_parmComparator);
        for (Parm source : anticipatedParms) {
            Parm found = null;
            for (Parm p : receivedParms) {
                if (p.getParmName().equals(source.getParmName()))
                    found = p;
            }
            assertNotNull("parameter " + source.getParmName() + " must be found on the received event", found);
            if (source.getValue().getContent() == null) source.getValue().setContent("null");
            assertEquals("content must match for parameter " + source.getParmName(), source.getValue().getContent(), found.getValue().getContent());
        }
    }
    
    private void resetAnticipator() {
        m_anticipator.reset();
        m_anticipatedEvents.clear();
    }

    private SnmpIfData createSnmpIfData(String ipAddress, String ifName, Long ifSpeed, Integer ifIndex) {
        OnmsNode node = new OnmsNode();
        node.setId(1);
        node.setLabel("testNode");
        OnmsSnmpInterface snmpIface = new OnmsSnmpInterface(ipAddress, ifIndex, node);
        snmpIface.setIfDescr(ifName);
        snmpIface.setIfName(ifName);
        snmpIface.setIfAlias(ifName);
        snmpIface.setIfSpeed(ifSpeed);
        return new SnmpIfData(snmpIface);
    }
    
    private void setupSnmpInterfaceDatabase(String ipAddress, String ifName) throws Exception {
        MockNetwork network = new MockNetwork();
        network.setCriticalService("ICMP");
        network.addNode(1, "testNode");
        network.addInterface(ipAddress);
        if (ifName != null)
            network.setIfAlias(ifName);
        network.addService("ICMP");
        network.addService("SNMP");
        network.addService("HTTP");
        MockDatabase db = new MockDatabase();
        db.populate(network);
        if (ifName != null)
            db.update("update snmpinterface set snmpifname=?, snmpifdescr=? where id=?", ifName, ifName, 1);
        DataSourceFactory.setInstance(db);
        Vault.setDataSource(db);
    }

    private boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        return path.delete();
    }

    private ThreadCategory log() {
        return ThreadCategory.getInstance(getClass());
    }

}
