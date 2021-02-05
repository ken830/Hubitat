/**
 * Hikvision HTTP Data Transmission Receiver - Alarm
 * 
 * Copyright 2021 Kenneth Leung (ken830)
 * 
 * ** Hikvision Camera Setup **
 * 1. Configuration -> Network -> Advanced Settings -> HTTP Listening: DestinationIP = [HubitatIP], URL = "/", Port = 39501
 * 2. Enable "Notify Surveillance Center" under Linkage Method for each event
 *
 */

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
		
        command "resetAlerts"

    }
}

preferences {
    input name: "hikIP", type: "string", title:"<b>Camera IP Address</b>", description: "<div><i></i></div><br>", required: true
    input name: "resetTime", type: "number", title:"<b>Alert Reset Time</b> (sec)", description: "<div><i></i></div><br>", defaultValue: 0
	input name: "eventTypeEnable", type: "enum", title:"<b>Event Type Filter</b> (Multiple Allowed)", description: "<div><i></i></div><br>", multiple: true , options: eventTypes
	input name: "eventTypeInvert", type: "bool", title:"<b>Invert Event Type Filter</b>", description: "<div><i>DISABLED: Trigger Only on Event Types Selected in Filter<br>ENABLED: Trigger on All Events Except Those Selected in Filter</i></div><br>", defaultValue: true

	
	input name: "loggingEnabled", type: "bool", title: "<b>Enable Logging</b>", description: "<div><i></i></div><br>", defaultValue: false
}

def installed() {
    logDebug "Hikvision Alarm device installed"
	
	configure()
}

def updated() {
	logDebug "Hikvision Alarm device updated"
	
	configure()
}

def configure() {
	unschedule()
	
	//Clear all state variables
	state.clear()
	
	//Clear all existing alerts
	resetAlerts()
	
	//Configure DNI
	setNetworkAddress()
	
	state.eventEnables = "$settings.eventTypeEnable"
	
}

def resetAlerts() {
	//Clear current states
	sendEvent(name: "motion", value: "inactive")
	
	//state.motionAlarm = false
}

def parse(String description) {
    logDebug "Parsing '${description}'"

	def msg = parseLanMessage(description)   
    def body = new XmlSlurper().parseText(new String(msg.body))
    logDebug groovy.xml.XmlUtil.escapeXml(msg.body)
	
	String eventType = body.eventType.text()
	log.info "Event Type: ${eventType}"
	state.lastEventType = eventType
	
	log.info "Alert Active"
	sendEvent(name: "motion", value: "active")
	state.motionAlarm = true
	
	//Trigger the inactive state in the future
	//Note: Hikvision cameras appear to only messages (~1/sec) when an alarm is in the active state.
	//		We need to reset this virtual device's current state with a pre-determined
	//		timeout period after the last alarm message was received.
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
    state.hubIP = "${location.hub.localIP}:39501"
	state.camDNI = "$dni"
}


void alertInactive() {
	logDebug "HTTP Messages Timeout. Assuming Alert State Inactive"
	log.info "Alert Inactive"
	
	resetAlerts()
}

void watchdog() {
    if (state.lastReport != null) {
        // check if there have been any messages in the last 1 minute
        if(state.lastReport >= now() - (1 * 60 * 1000)) {
            // OKAY
            logDebug "watchdog: OKAY"
        }
        else {
            // FAULT
            log.warn "watchdog: FAULT"
            // if we don"t receive any messages within 1 minute
            // set state to inactive
			//sendEvent(name: "motion", value: "inactive")
        }
    }
    else {
        log.info "No previous reports. Cannot determine health."
    }
}

private Integer convertHexToInt(hex) {
    return hex ? new BigInteger(hex[2..-1], 16) : 0
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( "." ).collect {  String.format( "%02x", it.toInteger() ) }.join()
    return hex.toUpperCase()
}

private String utc2000ToDate(int seconds) {
    int unix200Time = 946684800
    // <UNIX time> = <2000 time> + <January 1, 2000 UNIX time>
    int unixSeconds = seconds + unix200Time
    long unixMilliseconds = unixSeconds * 1000L
    new Date(unixMilliseconds).format("yyyy-MM-dd h:mm", location.timeZone)
}

void logDebug(str) {
    if (loggingEnabled) {
        log.debug str
    }
}

@Field static List eventTypes = ["IO","VMD","tamperdetection","diskfull","diskerror","nicbroken","ipconflict","illaccess","linedetection","fielddetection","videomismatch","badvideo","PIR"]

