/**
 * Hikvision HTTP Data Transmission Receiver - Alarm
 * Hubitat Device Driver
 *
 * https://raw.githubusercontent.com/ken830/Hubitat/main/drivers/Hikvision%20Alarm%20Driver/hikvision-alarm.driver.groovy
 *
 * Copyright 2021 Kenneth Leung (kleung1, ken830)
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * If this is useful, consider a donation: https://paypal.me/kleung1
 *
 * **Description **
 * Driver to receive alarms from Hikvision cameras with the "HTTP Listening"/"HTTP Data Transmission"/"Alarm Server" feature.
 *
 * ** Hikvision Camera Setup **
 * 1. Configuration -> Network -> Advanced Settings -> HTTP Listening: DestinationIP = <HubitatHubIP>, URL = "/", Port = 39501
 * 2. Enable "Notify Surveillance Center" under Linkage Method for each event
 *
 * ** Device Driver Setup **
 * 1. Install driver on hub
 * 2. Add Virtual Device and Select this Driver 
 * 3. Enter Camera IP Address and click "Save Preferences"
 * 4. Optionally, configure the sensors and buttons (see notes below)
 *
 * ** Notes **
 * - Three independent sensor capabilities: Motion, Presence, & Contact
 * 		- Each sensor has the following independent settings:
 * 			- Event Type Filter
 *			- Inclusive vs Exclusive Filter Setting
 *			- Sensor State Inversion Setting
 *      	- Alert Reset Time
 * 		- By default, the filters are empty and exclusive, so all event types will trigger all three sensors.
 * 		- To disable a sensor, leave the filter empty and make it inclusive
 * - Six independent buttons
 *		- Each button has the following independent settings:
 *			- Event Type Filter
 *			- Reset Time (for preventing multiple triggers in quick succession)
 *		- By default, the buttons filters are empty, so nothing will trigger any of them
 *
 * ** Version History **
 * 2021-02-07: v1.0.0 - Initial Release
 * 2021-02-08: v1.0.1 - Updated Instructions to include the renamed "Alarm Server" feature. Moved preferences{} inside metadata{}.
 * 2021-02-15: v1.1.0 - Significant update to add 6 customizable buttons in addition to existing 3 sensors.
 *
 */

import groovy.transform.Field


metadata {
    definition(
		name: "Hikvision Alarm Buttons",
		namespace: "ken830",
		author: "Kenneth Leung",
		description: "HTTP Data Transmission Receiver - Alarm with Buttons"
	) {
		capability "Sensor"
		
		capability "MotionSensor"
		capability "PresenceSensor"
		capability "ContactSensor"
		capability "PushableButton"
		
        command "resetAlerts"
		command "button1"
		command "button2"
		command "button3"
		command "button4"
		command "button5"
		command "button6"

    }

	preferences {
		input name: "hikIP", type: "string", title:"<b>Camera IP Address</b>", description: "<div><i></i></div><br>", required: true
		
		input name: "infoLoggingEnabled", type: "bool", title: "<b>Enable Info Logging</b>", description: "<div><i></i></div><br>", defaultValue: true
		input name: "debugLoggingEnabled", type: "bool", title: "<b>Enable Debug Logging</b>", description: "<div><i>Disables in 15 minutes</i></div><br>", defaultValue: false

		input name: "eventTypeFilterMotion", type: "enum", title:"<b>Motion Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeInvertMotion", type: "bool", title:"<b>Motion Sensor</b>", description: "<div><i>Exclusive Filter (Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
		input name: "sensorInvertMotion",    type: "bool", title:"<b>Motion Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false
		
		input name: "eventTypeFilterPresence", type: "enum", title:"<b>Presence Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeInvertPresence", type: "bool", title:"<b>Presence Sensor</b>", description: "<div><i>Exclusive Filter(Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
		input name: "sensorInvertPresence",    type: "bool", title:"<b>Presence Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false
		
		input name: "eventTypeFilterContact", type: "enum", title:"<b>Contact Sensor</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeInvertContact", type: "bool", title:"<b>Contact Sensor</b>", description: "<div><i>Exclusive Filter(Trigger on All Events Except Those Selected)</i></div><br>", defaultValue: true
		input name: "sensorInvertContact",    type: "bool", title:"<b>Contact Sensor</b>", description: "<div><i>Invert Sensor Output State</i></div><br>", defaultValue: false

		input name: "resetTimeMotion", type: "number", title:"<b>Motion Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimePresence", type: "number", title:"<b>Presence Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimeContact", type: "number", title:"<b>Contact Sensor</b>", description: "<div><i>Alert Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		
		input name: "eventTypeFilterBtn1", type: "enum", title:"<b>Button 1</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeFilterBtn2", type: "enum", title:"<b>Button 2</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeFilterBtn3", type: "enum", title:"<b>Button 3</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		
		input name: "resetTimeBtn1", type: "number", title:"<b>Button 1</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimeBtn2", type: "number", title:"<b>Button 2</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimeBtn3", type: "number", title:"<b>Button 3</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		
		input name: "eventTypeFilterBtn4", type: "enum", title:"<b>Button 4</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeFilterBtn5", type: "enum", title:"<b>Button 5</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		input name: "eventTypeFilterBtn6", type: "enum", title:"<b>Button 6</b>", description: "<div><i>Event Type Filter(Multiple Selections Allowed)</i></div><br>", multiple: true , options: eventTypes
		
		input name: "resetTimeBtn4", type: "number", title:"<b>Button 4</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimeBtn5", type: "number", title:"<b>Button 5</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0
		input name: "resetTimeBtn6", type: "number", title:"<b>Button 6</b>", description: "<div><i>Reset Time</i></div><br>", range: "0..604800", defaultValue: 0

	}
}
	
	
def installed() {
    logDebug "Hikvision Alarm with Buttons device installed"
		
	configure()
}

def updated() {
	logDebug "Hikvision Alarm with Buttons device updated"
	
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
	
	state.eventTypeFilterBtn1 = "$settings.eventTypeFilterBtn1"
	state.eventTypeFilterBtn2 = "$settings.eventTypeFilterBtn2"
	state.eventTypeFilterBtn3 = "$settings.eventTypeFilterBtn3"
	state.eventTypeFilterBtn4 = "$settings.eventTypeFilterBtn4"
	state.eventTypeFilterBtn5 = "$settings.eventTypeFilterBtn5"
	state.eventTypeFilterBtn6 = "$settings.eventTypeFilterBtn6"
	
	state.enableBtn1 = true
	state.enableBtn2 = true
	state.enableBtn3 = true
	state.enableBtn4 = true
	state.enableBtn5 = true
	state.enableBtn6 = true
	

	// Clear all existing alerts
	resetAlerts()
	
	// Set the number of buttons
	sendEvent(name:"numberOfButtons", value:6)
	
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

def button1() {
	sendEvent(name:"pushed", value:1, isStateChange: true)
}

def button2() {
	sendEvent(name:"pushed", value:2, isStateChange: true)
}

def button3() {
	sendEvent(name:"pushed", value:3, isStateChange: true)
}

def button4() {
	sendEvent(name:"pushed", value:4, isStateChange: true)
}

def button5() {
	sendEvent(name:"pushed", value:5, isStateChange: true)
}

def button6() {
	sendEvent(name:"pushed", value:6, isStateChange: true)
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
	

	// Match Motion Filter
	if ((eventType in settings.eventTypeFilterMotion) ^ settings.eventTypeInvertMotion) {
		infoMotion = "M[X] "
		sendEvent(name: "motion", value: "$state.motionON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimeMotion.toInteger(), resetAlertMotion, overwrite)
	}
	else{
		infoMotion = "M[ ] "
	}
	
	// Match Presence Filter
	if ((eventType in settings.eventTypeFilterPresence) ^ settings.eventTypeInvertPresence) {
		infoPresence = "P[X] "
		sendEvent(name: "presence", value: "$state.presenceON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimePresence.toInteger(), resetAlertPresence, overwrite)
	}
	else{
		infoPresence = "P[ ] "
	}
	
	// Match Contact Filter
	if ((eventType in settings.eventTypeFilterContact) ^ settings.eventTypeInvertContact) {
		infoContact = "C[X]"
		sendEvent(name: "contact", value: "$state.contactON")
		
		// Trigger the inactive state after [2 sec] + Alert Reset Time
		runIn(2 + settings.resetTimeContact.toInteger(), resetAlertContact, overwrite)
	}
	else{
		infoContact = "C[ ]"
	}
	
	// Match Button Filters
	if ((eventType in settings.eventTypeFilterBtn1) && state.enableBtn1) {
		state.enableBtn1 = false
		runIn(settings.resetTimeBtn1, restoreBtn1, overwrite)
		
		infoBtn1 = "1[X] "
		sendEvent(name:"pushed", value:1, isStateChange: true)
	}
	else{
		infoBtn1 = "1[ ] "
	}
	
	if (eventType in settings.eventTypeFilterBtn2 && state.enableBtn2) {
		state.enableBtn2 = false
		runIn(settings.resetTimeBtn2, restoreBtn2, overwrite)
		
		infoBtn2 = "2[X] "
		sendEvent(name:"pushed", value:2, isStateChange: true)
	}
	else{
		infoBtn2 = "2[ ] "
	}
	
	if ((eventType in settings.eventTypeFilterBtn3) && state.enableBtn3) {
		state.enableBtn3 = false
		runIn(settings.resetTimeBtn3, restoreBtn3, overwrite)
		
		infoBtn3 = "3[X] "
		sendEvent(name:"pushed", value:3, isStateChange: true)
	}
	else{
		infoBtn3 = "3[ ] "
	}
	
	if (eventType in settings.eventTypeFilterBtn4 && state.enableBtn4) {
		state.enableBtn4 = false
		runIn(settings.resetTimeBtn4, restoreBtn4, overwrite)
		
		infoBtn4 = "4[X] "
		sendEvent(name:"pushed", value:4, isStateChange: true)
	}
	else{
		infoBtn4 = "4[ ] "
	}
	
	if (eventType in settings.eventTypeFilterBtn5 && state.enableBtn5) {
		state.enableBtn5 = false
		runIn(settings.resetTimeBtn5, restoreBtn5, overwrite)
		
		infoBtn5 = "5[X] "
		sendEvent(name:"pushed", value:5, isStateChange: true)
	}
	else{
		infoBtn5 = "5[ ] "
	}
	if (eventType in settings.eventTypeFilterBtn6 && state.enableBtn6) {
		state.enableBtn6 = false
		runIn(settings.resetTimeBtn6, restoreBtn6, overwrite)
		
		infoBtn6 = "6[X]"
		sendEvent(name:"pushed", value:6, isStateChange: true)
	}
	else{
		infoBtn6 = "6[ ]"
	}
	
	
	logInfo "Triggered: " + infoMotion + infoPresence + infoContact
	logInfo "ButtonsPushed: " + infoBtn1 + infoBtn2 + infoBtn3 + infoBtn4 + infoBtn5 + infoBtn6
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

def restoreBtn1 () {
	state.enableBtn1 = true
}

def restoreBtn2 () {
	state.enableBtn2 = true
}

def restoreBtn3 () {
	state.enableBtn3 = true
}

def restoreBtn4 () {
	state.enableBtn4 = true
}

def restoreBtn5 () {
	state.enableBtn5 = true
}

def restoreBtn6 () {
	state.enableBtn6 = true
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