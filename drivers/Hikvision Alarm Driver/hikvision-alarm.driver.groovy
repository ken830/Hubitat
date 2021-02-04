// Hikvision HTTP Data Transmission Receiver - Alarm

// Enable "Notify Surveillance Center" under Linkage Method for each event

import groovy.transform.Field


metadata {
    definition(
		name: "Hikvision Alarm",
		namespace: "ken830",
		author: "Kenneth L",
		description: "HTTP Data Transmission Receiver - Alarm"
	) {
		capability "MotionSensor"
		capability "PresenceSensor"
		
        command 'resetAlerts'

    }
}

preferences {
    input name: "hikIP", type: 'string', title:'<b>Camera IP Address</b>', description: '<div><i></i></div><br>', required: true
    input name: "resetTime", type: "number", title:"<b>Alert Reset Time (sec)</b>", description: '<div><i></i></div><br>', defaultValue: 0
	
	input name: 'loggingEnabled', type: 'bool', title: '<b>Enable Logging</b>', description: '<div><i></i></div><br>', defaultValue: false
}

def installed() {
    log.debug 'Hikvision Alarm device installed'
	
	configure()
}

def updated() {
	log.debug 'Hikvision Alarm device updated'

	configure()
}

def configure() {
	unschedule()
	
    state.remove('connectionStatus')
	
	setNetworkAddress()

	// Clear all existing alerts
	resetAlerts()
	
	// Run Watchdog check every 1 minute
    runEvery1Minute('watchdog')
}

def resetAlerts() {
	sendEvent(name: "motion", value: "inactive")
}

def parse(String description) {
    logDebug "Parsing '${description}'"

	def msg = parseLanMessage(description)   
    def body = new XmlSlurper().parseText(new String(msg.body))
    logDebug groovy.xml.XmlUtil.escapeXml(msg.body) 
	
	log.info "Alert Active"
	sendEvent(name: "motion", value: "active")
	
	//Trigger the inactive state in the future (overridable)
	runIn(2 + settings.resetTime.toInteger(), alertInactive, overwrite)
	
	state.lastReport = now()
}

void setNetworkAddress() {
    // Setting Network Device Id
    def dni = convertIPtoHex(settings.hikIP)
    if (device.deviceNetworkId != "$dni") {
        device.deviceNetworkId = "$dni"
        log.debug "Device Network Id set to ${device.deviceNetworkId}"
    }

    // set hubitat endpoint
    //state.hubUrl = "http://${location.hub.localIP}:39501"
}


void alertInactive() {
	logDebug "HTTP Messages Timeout. Assuming Alert State Inactive"
	log.info "Alert Inactive"
	
	sendEvent(name: "motion", value: "inactive")
}

void watchdog() {
    if (state.lastReport != null) {
        // check if there have been any messages in the last 1 minute
        if(state.lastReport >= now() - (1 * 60 * 1000)) {
            // OKAY
            logDebug 'watchdog: OKAY'
        }
        else {
            // FAULT
            log.warn 'watchdog: FAULT'
            // if we don't receive any messages within 1 minute
            // set state to inactive
			//sendEvent(name: "motion", value: "inactive")
        }
    }
    else {
        log.info 'No previous reports. Cannot determine health.'
    }
}

private Integer convertHexToInt(hex) {
    return hex ? new BigInteger(hex[2..-1], 16) : 0
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()
}

private String utc2000ToDate(int seconds) {
    int unix200Time = 946684800
    // <UNIX time> = <2000 time> + <January 1, 2000 UNIX time>
    int unixSeconds = seconds + unix200Time
    long unixMilliseconds = unixSeconds * 1000L
    new Date(unixMilliseconds).format('yyyy-MM-dd h:mm', location.timeZone)
}

void logDebug(str) {
    if (loggingEnabled) {
        log.debug str
    }
}