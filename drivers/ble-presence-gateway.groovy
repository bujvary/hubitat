/**
 *  ****************  BLE Presence Gateway  ****************
 *
 *  Design Usage:
 *  This driver is used to get BLE beacon broadcast data from a BLE Wifi Gateway that posts data to a MQTT broker.
 *
 *  Copyright 2019 Brian Ujvary
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
 *  1.0.5 - Modified to only send one lastUpdated event per JSON payload from BLE gateway
 *  1.0.4 - Renamed the driver and updated the importURL
 *  1.0.3 - Added logic to pass the RSSI to the child parse routine
 *  1.0.2 - Added retry logic if client gets disconnected
 *  1.0.1 - Modified to call child parse function,
 *          added connect() and disconnect(),
 *          added error handling in mqttClientStatus(),
 *          removed commented code
 *  1.0.0 - Initial release
 */

metadata {
    definition (name: "BLE Presence Gateway",
                namespace: "bujvary",
                author: "Brian Ujvary",
                importURL: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/ble-presence-gateway.groovy") {
        capability "Initialize"
        
        // State of the connection to the MQTT broker ("connected" or "disconnected").
        attribute "connection", "STRING"

        command "publishMsg", ["String"]
    }

    preferences {
        input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
        input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "clientid", type: "text", title: "MQTT Client ID:", description: "(blank if none)", required: false, displayDuringSetup: true
        input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: false, displayDuringSetup: true
        input name: "topicPub", type: "text", title: "Topic to Publish:", description: "Topic Value (topic/device/value)", required: false, displayDuringSetup: true
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
    removeChildDevices()
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
    
    def payloadJson = parseJson(payload)
    if (logEnable) log.debug "parse: payloadJson " + payloadJson
    
    payloadJson.each { beacon ->
        if (logEnable) log.debug "mac: ${beacon.mac}"
        def macDNI = "ble:" + beacon.mac
        def macChild = getChildDevice(macDNI)
        if (macChild == null) {
            if (logEnable) log.warn "parse: child presence sensor does not exist for " + macDNI
            macChild = addChildDevice("bujvary", "Virtual Presence with Timeout", macDNI, [label: "Vehicle Presence Sensor", isComponent: false])
        }
        
        if (logEnable) log.debug "parse: updating presence sensor data for " + macDNI
        macChild.parse(beacon.rssi)
    }
    
    if (logEnable) log.debug "Sending event: name -> lastUpdated, value -> ${date.toString()}"
    sendEvent(name: "lastUpdated", value: "${date.toString()}", displayed: true)
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

def mqttClientStatus(String message){
    if (message.startsWith("Error:")) {
        log.error "mqttClientStatus: ${message}"
        disconnect()
        runIn (5,"connect")
    } else {
        log.info "mqttClientStatus: ${message}"
    }

}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

private removeChildDevices() {
  log.debug "Removing child devices"
    try {
        getChildDevices()?.each {
          try {
              deleteChildDevice(it.deviceNetworkId)
            } catch (e) {
                log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
            }
        }
    } catch (err) {
        log.debug "Either no children exist or error finding child devices for some reason: ${err}"
    }
}
