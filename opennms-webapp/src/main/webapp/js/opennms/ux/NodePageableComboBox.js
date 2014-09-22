Ext.BLANK_IMAGE_URL =  "extJS/resources/images/default/s.gif";
Ext.namespace("OpenNMS.ux");
OpenNMS.ux.NodePageableComboBox=Ext.extend(OpenNMS.ux.ComboFilterBox,{
	
	url:"rest/nodes",
	recordMap:[
		{name:"name", mapping:"@label"},
		{name:"id", mapping:"@id"}
	],
	hideTrigger:false,
	queryParam:"label",
	minHeight:300,
	triggerAction:"all",
	emptyText:"-- Choose A Node --",
	width:220
	
});

Ext.reg('o-nodepageablecombo', OpenNMS.ux.NodePageableComboBox);