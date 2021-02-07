/**
 * Hikvision HTTP Data Transmission Receiver - Alarm
 * Hubitat Device Driver
 *
 * https://raw.githubusercontent.com/ken830/Hubitat/main/drivers/Hikvision%20Alarm%20Driver/hikvision-alarm.driver.groovy
 *
 * Copyright 2021 Kenneth Leung (kleung1, ken830)
 * 
 * **Description **
 * Driver to receive alarms from Hikvision cameras with the "HTTP Listening"/"HTTP Data Transmission" feature.
 *
 * ** Hikvision Camera Setup **
 * 1. Configuration -> Network -> Advanced Settings -> HTTP Listening: DestinationIP = <HubitatHubIP>, URL = "/", Port = 39501
 * 2. Enable "Notify Surveillance Center" under Linkage Method for each event
 *
 * ** Device Driver Setup **
 * 1. Install driver on hub
 * 2. Add Virtual Device and Select this Driver 
 * 3. Enter Camera IP Address and click "Save Preferences"
 * 4. Optionally, configure the sensors (see notes below)
 *
 * ** Notes **
 * - Three independent sensor capabilities: Motion, Presence, & Contact
 * - Each sensor has the following independent settings:
 * 		- Event Type Filter
 *		- Inclusive vs Exclusive Filter Setting
 *		- Sensor State Inversion Setting
 *      - Alert Reset Time
 * - By default, the filters are empty and exclusive, so all event types will trigger all three sensors.
 * - To disable a sensor, leave the filter empty and make it inclusive
 *
 */

import groovy.transform.Field


metadata {
    definition(
		name: "Hikvision Alarm",
		namespace: "ken830",
		author: "Kenneth Leung",
		description: "HTTP Data Transmission Receiver - Alarm"
	) {
		capability "Sensor"
		
		capability "MotionSensor"
		capability "PresenceSensor"
		capability "ContactSensor"
		
        command "resetAlerts"

    }
}

preferences {
    input name: "hikIP", type: "string", title:"<b>Camera IP Address</b>", description: "<div><i></i></div><br>", required: true
    
	input name: "infoLoggingEnabled", type: "bool", title: "<b>Enable Info Logging</b>", description: "<div><i></i></div><br>", defaultValue: true
	input name: "debugLoggingEnabled", type: "bool", title: "<b>Enable Debug Logging</b>", description: "<div><i>Disables in 15 minutes</i></div><br>", defaultValue: false

	input name: "resetTimeMotion", type: "number", title:"<b>Motion Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
	input name: "resetTimePresence", type: "number", title:"<b>Presence Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
	input name: "resetTimeContact", type: "number", title:"<b>Contact Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
	
	input name: "eventTypeFilterMotion", type: "enum", title:"<b>Motion Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
	input name: "eventTypeInvertMotion", type: "bool", title:"<b>Motion Sensor</b>", description: "<div><i>Exclusive Filter (Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
	input name: "sensorInvertMotion",    type: "bool", title:"<b>Motion Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false
	
	input name: "eventTypeFilterPresence", type: "enum", title:"<b>Presence Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
	input name: "eventTypeInvertPresence", type: "bool", title:"<b>Presence Sensor</b>", description: "<div><i>Exclusive Filter(Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
	input name: "sensorInvertPresence",    type: "bool", title:"<b>Presence Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false
	
	input name: "eventTypeFilterContact", type: "enum", title:"<b>Contact Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
	input name: "eventTypeInvertContact", type: "bool", title:"<b>Contact Sensor</b>", description: "<div><i>Exclusive Filter(Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
	input name: "sensorInvertContact",    type: "bool", title:"<b>Contact Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false
	
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

	//Set state variables
	if (settings.sensorInvertMotion){
		state.motionOFF = "active"
		state.motionON = "inactive"
	}
	else {
		state.motionOFF = "inactive"
		state.motionON = "active"
	}
	
	if (settings.sensorInvertPresence){
		state.presenceOFF = "present"
		state.presenceON = "not present"
	}
	else {
		state.presenceOFF = "not present"
		state.presenceON = "present"
	}
	
	if (settings.sensorInvertContact){
		state.contactOFF = "open"
		state.contactON = "closed"
	}
	else {
		state.contactOFF = "closed"
		state.contactON = "open"
	}
	
	state.eventFilterMotion = "$settings.eventTypeFilterMotion"
	state.eventFilterPresence = "$settings.eventTypeFilterPresence"
	state.eventFilterContact = "$settings.eventTypeFilterContact"

	// Clear all existing alerts
	resetAlerts()
	
	// Configure DNI
	setNetworkAddress()

	// Disable debug logging in 30 minutes
    if (settings.debugLoggingEnabled) runIn(1800, disableLogging)
}

def resetAlerts() {
	// Clear current states
	resetAlertMotion()
	resetAlertPresence()
	resetAlertContact()
}

def resetAlertMotion() {
	logInfo "MOTION SENSOR: Alert Inactive"
	sendEvent(name: "motion", value: "$state.motionOFF")
}

def resetAlertPresence() {
	logInfo "PRESENCE SENSOR: Alert Inactive"
	sendEvent(name: "presence", value: "$state.presenceOFF")
}

def resetAlertContact() {
	logInfo "CONTACT SENSOR: Alert Inactive"
	sendEvent(name: "contact", value: "$state.contactOFF")
}

def parse(String description) {
    logDebug "Parsing '${description}'"

	def msg = parseLanMessage(description)   
    def body = new XmlSlurper().parseText(new String(msg.body))
    logDebug groovy.xml.XmlUtil.escapeXml(msg.body)
	
	String eventType = body.eventType.text()
	String eventDateTime = body.dateTime.text()
	logInfo "Event Type: ${eventType} (${eventDateTime})"
	
	
	//state.lastEventType = eventType
	
	// Match Motion Filter
	if ((eventType in settings.eventTypeFilterMotion) ^ settings.eventTypeInvertMotion) {
		//logInfo "MOTION SENSOR: Alert Active"
		infoMotion = "M[X] "
		sendEvent(name: "motion", value: "$state.motionON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimeMotion.toInteger(), resetAlertMotion, overwrite)
	}
	else{
		//logInfo "MOTION SENSOR: Filtered Event - Not Triggered"
		infoMotion = "M[ ] "
	}
	
	// Match Presence Filter
	if ((eventType in settings.eventTypeFilterPresence) ^ settings.eventTypeInvertPresence) {
		//logInfo "PRESENCE SENSOR: Alert Active"
		infoPresence = "P[X] "
		sendEvent(name: "presence", value: "$state.presenceON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimePresence.toInteger(), resetAlertPresence, overwrite)
	}
	else{
		//logInfo "PRESENCE SENSOR: Filtered Event - Not Triggered"
		infoPresence = "P[ ] "
	}
	
	// Match Contact Filter
	if ((eventType in settings.eventTypeFilterContact) ^ settings.eventTypeInvertContact) {
		//logInfo "CONTACT SENSOR: Alert Active"
		infoContact = "C[X]"
		sendEvent(name: "contact", value: "$state.contactON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimeContact.toInteger(), resetAlertContact, overwrite)
	}
	else{
		//logInfo "CONTACT SENSOR: Filtered Event - Not Triggered"
		infoContact = "C[ ]"
	}
	
	log.info "Triggered: " + infoMotion + infoPresence + infoContact
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



private Integer convertHexToInt(hex) {
    return hex ? new BigInteger(hex[2..-1], 16) : 0
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( "." ).collect {  String.format( "%02x", it.toInteger() ) }.join()
    return hex.toUpperCase()
}

void disableLogging() {
	log.info 'Logging disabled.'
	device.updateSetting('debugLoggingEnabled',[value:'false',type:'bool'])
}

void logInfo(str) {
    if (infoLoggingEnabled) {
        log.info str
    }
}

void logDebug(str) {
    if (debugLoggingEnabled) {
        log.debug str
    }
}

// eventType list compiled from a variety of Hikvision cameras I have on-hand (DS-2CD2432F-IW, DS-2CD2532F-IS, & DS-2CD2347G1-LU) and likely missing some from other more capable cameras.
@Field static List eventTypes = ["IO","VMD","tamperdetection","diskfull","diskerror","nicbroken","ipconflict","illaccess","linedetection","fielddetection","videomismatch","badvideo","facedetection","unattendedBaggage","attendedBaggage","storageDetection","scenechangedetection","faceSnap","PIR"]

/*
Basic/Smart Event Names in the Camera's GUI:

"IO" = (Local) Alarm Input
"VMD" = (Video) Motion Detection
"tamperdections" = Video Tampering ??
"diskfull" = HDD Full
"diskerror" = HDD Error
"nicbroken" = Network Disconnected
"ipconflict" = IP Address Conflicted
"illaccess" = Illegal Login
"linedetection" = Line Crossing Detection
"fielddetection" = Intrusion Detection ??
"videomismatch" = 
"badvideo" = 
"facedetection" = Face Detection
"unattendedBaggage" = Unattended Baggage Detection
"attendedBaggage" = Object Removal Detection ??
"storageDetection"
"scenechangedetection" = Scene Change Detection
"faceSnap"
"PIR" = PIR Alarm
*/