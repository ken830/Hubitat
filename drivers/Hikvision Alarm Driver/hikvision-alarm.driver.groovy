/**
 * Hikvision HTTP Data Transmission Receiver - Alarm
 * Hubitat Device Driver
 *
 * https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/logitech-harmony-hub-parent.src/logitech-harmony-hub-parent.groovy
 *
 * Copyright 2021 Kenneth Leung (kleung1, ken830)
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
    input name: "resetTime", type: "number", title:"<b>Alert Reset Time</b> (sec)", description: "<div><i></i></div><br>", defaultValue: 0

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
	
	//Clear all existing alerts
	resetAlerts()
	
	//Configure DNI
	setNetworkAddress()
	
	// disable debug logging in 30 minutes
    if (settings.debugLoggingEnabled) runIn(1800, disableLogging)
	
	state.eventFilterMotion = "$settings.eventTypeFilterMotion"
	state.eventFilterPresence = "$settings.eventTypeFilterPresence"
	state.eventFilterContact = "$settings.eventTypeFilterContact"

}

def resetAlerts() {
	//Clear current states
	if (settings.sensorInvertMotion){
		sendEvent(name: "motion", value: "active")
	}
	else {
		sendEvent(name: "motion", value: "inactive")
	}
	
	if (settings.sensorInvertPresence){
		sendEvent(name: "presence", value: "present")
		}
	else {
		sendEvent(name: "presence", value: "not present")
	}
	
	if (settings.sensorInvertContact){
		sendEvent(name: "contact", value: "open")
	}
	else {
		sendEvent(name: "contact", value: "closed")
	}
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
	
    //log.info (eventType in settings.eventTypeFilterMotion)
	if ((eventType in settings.eventTypeFilterMotion) ^ settings.eventTypeInvertMotion) {
		//log.info "Triggered"
		log.info "Alert Active"
		sendEvent(name: "motion", value: "active")
		sendEvent(name: "presence", value: "present")
		sendEvent(name: "contact", value: "open")
		//state.motionAlarm = true
		
		//Trigger the inactive state in the future
		//Note: Hikvision cameras appear to only send messages (~1/sec) when an alarm is in the active state.
		//		We need to reset this virtual device's current state with a pre-determined
		//		timeout period after the last alarm message was received.
		runIn(2 + settings.resetTime.toInteger(), alertInactive, overwrite)
	}
	else{
		log.info "Filtered Event - Not Triggered"
	}
	
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

void logDebug(str) {
    if (debugLoggingEnabled) {
        log.debug str
    }
}

// eventType list compiled from a variety of Hikvision cameras I have on-hand (DS-2CD2432F-IW, DS-2CD2532F-IS, & DS-2CD2347G1-LU) and likely missing some from other more capable cameras.
@Field static List eventTypes = ["IO","VMD","tamperdetection","diskfull","diskerror","nicbroken","ipconflict","illaccess","linedetection","fielddetection","videomismatch","badvideo","facedetection","unattendedBaggage","attendedBaggage","storageDetection","scenechangedetection","faceSnap","PIR"]
