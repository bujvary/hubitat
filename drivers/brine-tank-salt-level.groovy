/**
 *  ****************  Brine Tank Salt Level Driver  ****************
 *
 *  Design Usage:
 *  This driver is used to get the salt level data from a water softener brine tank that has an Arduino with a distance
 *  sensor monitoring the level. The Arduino posts the salt level data to a MQTT broker.
 *
 *  Copyright 2020 Brian Ujvary
 *
 *  Based on work by Aaron Ward, Kirk Rader
 *  
 *  This driver is free and you may do as you like with it.  
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *
 *  Changes:
 *  1.0.0 - Initial release
 */

metadata {
    definition (name: "Brine Tank Salt Level",
                namespace: "bujvary",
                author: "Brian Ujvary",
                importURL: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/brine-tank-salt-level.groovy") {
        capability "Initialize"
        
        // State of the connection to the MQTT broker ("connected" or "disconnected").
        attribute "connection", "string"
        attribute "lastUpdated", "string"
        attribute "percentFull", "number"
        attribute "saltTile", "string"

        command "publishMsg", ["String"]
        //command "tileNow"
    }

    preferences {
        input name: "emptyLevel", type: "text", title: "Softner Tank Empy Level:", description: "(in millimeters)", required: true, displayDuringSetup: true
        input name: "fullLevel", type: "text", title: "Softner Tank Full Level:", description: "(in millimeters)", required: true, displayDuringSetup: true
        input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
        input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "clientid", type: "text", title: "MQTT Client ID:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/value)", required: false, displayDuringSetup: true
        input name: "topicPub", type: "text", title: "Topic to Publish:", description: "Example Topic (topic/device/value)", required: false, displayDuringSetup: true
        input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
        input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
        input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    }
}

def installed() {
    log.info "${device.displayName}.installed()"
    
    if (settings.MQTTBroker?.trim()) {
        initialize()
    }
}

def updated() {
    if (logEnable) log.info "${device.displayName}.updated()"
    initialize()
}

def uninstalled() {
    if (logEnable) log.info "Disconnecting from mqtt"
    interfaces.mqtt.disconnect()
}

def initialize() {
    if (logEnable) runIn(900,logsOff)
    
    disconnect()
    connect()
}

// Parse incoming device messages to generate events
def parse(String description) {
    Date date = new Date();
    
    topic = interfaces.mqtt.parseMessage(description).topic
    topic = topic.substring(topic.lastIndexOf("/") + 1)
    
    payload = interfaces.mqtt.parseMessage(description).payload
    
    if (logEnable) log.debug "Topic: ${topic}"
    if (logEnable) log.debug "Payload: ${payload}"
    
    if (logEnable) log.debug "Sending event: name -> lastUpdated, value -> ${date.toString()}"
    sendEvent(name: "lastUpdated", value: "${date.toString()}", displayed: true)
    
    state.level = payload
    
    tileNow()
}

def publishMsg(String s) {
    if (logEnable) log.debug "Sent this: ${s} to ${settings?.topicPub} - QOS Value: ${settings?.QOS.toInteger()} - Retained: ${settings?.retained}"
    interfaces.mqtt.publish(settings?.topicPub, s, settings?.QOS.toInteger(), settings?.retained)
}

/*
    The connect(), disconnect(), mqttClientStatus() borrowed from:
    https://github.com/parasaurolophus/hubitat-mqtt-connection/blob/master/mqtt-connection-driver.groovy
*/
def connect() {
    log.debug "state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
    state.reconnect = true
    while (!state.connected && state.reconnect) {
        try {
            if(settings?.retained==null) settings?.retained=false
            if(settings?.QOS==null) setting?.QOS="1"
        
            //open connection
            mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
            interfaces.mqtt.connect(mqttbroker, settings?.clientid, settings?.username,settings?.password)
        
            //give it a chance to start
            pauseExecution(500)
            
            state.connected = true
            log.info "Connection established"
        
            if (logEnable) log.debug "Subscribed to: ${settings?.topicSub}"
            interfaces.mqtt.subscribe(settings?.topicSub)
            
            sendEvent(name: "connection", value: "connected")
        } catch(e) {
            if (logEnable) log.debug "Connect error: ${e.message}"
            runIn (5,"connect")
        }
    }
}

def disconnect() {
    log.debug "state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
    state.reconnect = false
    
    if (state.connected) {
        try {
            interfaces.mqtt.unsubscribe(settings?.topicSub)
        } catch (e) {
            log.error "Error unsubscribing: ${e.message}"
        }
    }    
    
    try {
        interfaces.mqtt.disconnect()
    } catch (e) {
        log.error "Error disconnecting: ${e.message}"
    }
        
    state.connected = false
    sendEvent(name: "connection", value: "disconnected")
    log.debug "state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
}

def mqttClientStatus(String message) {
    if (message.startsWith("Error:")) {
        log.error "mqttClientStatus: ${message}"
        disconnect()
        runIn (5,"connect")
    } else {
        log.info "mqttClientStatus: ${message}"
    }

}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def tileNow() { 
    if(emptyLevel==null || emptyLevel=="") emptyLevel="500"
    if(fullLevel==null || fullLevel=="") emptyLevel="600"
    
    intFullLevel = fullLevel.toInteger()
    intEmptyLevel = emptyLevel.toInteger()
    intCurLevel = state.level.toInteger()
    
    if (logEnable) log.debug "intCurLevel: ${intCurLevel}, intEmptyLevel = ${intEmptyLevel}, intFullLevel = ${intFullLevel}"
    
    if (intCurLevel >= intFullLevel)
        percentFull = 100
    else if (intCurLevel <= intEmptyLevel)
        percentFull = 0
    else
        percentFull = (int)(100 - ((intFullLevel - intCurLevel) / (intFullLevel - intEmptyLevel) * 100))
    
    if (percentFull <= 25)
        img = "salt-empty.png"

    if ((percentFull > 25) && (percentFull <= 75))
        img = "salt-half.png"
    
    if (percentFull > 75)
        img = "salt-full.png"
    
    img = "https://raw.githubusercontent.com/bujvary/hubitat/master/images/${img}"
    html = "<style>img.salttankImage { max-width:80%;height:auto;}div#salttankImgWrapper {width=100%}div#salttankWrapper {font-size:13px;margin: 30px auto; text-align:center;}</style><div id='salttankWrapper'>"
    html += "<div id='salttankImgWrapper'><center><img src='${img}' class='saltankImage'></center></div>"
    html += "Salt Level: ${percentFull}%</div>"

    sendEvent(name: "saltTile", value: html)
    sendEvent(name: "percentFull", value: percentFull)
}