/**
 *  ****************  BLE Presence Gateway  ****************
 *
 *  Design Usage:
 *  This driver is used to get BLE beacon broadcast data from a BLE Wifi Gateway that posts data to a MQTT broker.
 *
 *  Copyright 2021 Brian Ujvary
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
 *  1.1.0 - Refactored startup delay logic again
 *          added a generic component switch for vehicle presence override
 *          added logic to control the generic component switch in initialize(), connect() and disconnect()
 *  1.0.9 - Added option to disable the lastUpdated event
 *          added call to logsOff in updated function
 *  1.0.8 - Cleaned up debug messages
 *  1.0.7 - Refactored startup delay logic
 *          added reconnect()
 *  1.0.6 - Added a startup delay for the MQTT connection since the driver starts running before the systemStart event
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
        attribute "connection", "String"
        attribute "lastUpdated", "String"
        
        command "publishMsg", ["String"]
        command "reconnect"
    }

    preferences {
        input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
        input name: "username", type: "text", title: "MQTT Username:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "password", type: "password", title: "MQTT Password:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "clientid", type: "text", title: "MQTT Client ID:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "connectDelay", type: "integer", title: "MQTT Connect Delay:", description: "<div><i>On hub startup (in seconds)</i></div>", required: true, defaultValue: 60, displayDuringSetup: true
        input name: "overrideEnableDelay", type: "integer", title: "Vehicle Presence Override Enable Delay:", description: "<div><i>On MQTT connect (in seconds)</i></div>", required: true, defaultValue: 10, displayDuringSetup: true
        input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "<div><i>Example Topic (topic/device/#)</i></div>", required: false, displayDuringSetup: true
        input name: "topicPub", type: "text", title: "Topic to Publish:", description: "<div><i>Topic Value (topic/device/value)</i></div>", required: false, displayDuringSetup: true
        input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
        input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
        input name: "disableLastUpdated", type: "bool", title: "Disable LastUpdated Event", required: false, defaultValue: false, displayDuringSetup: true
        input name: "logEnable", type: "bool", title: "Enable logging", description: "<div><i>Automatically disables after 15 minutes</i></div>", required: true, defaultValue: true
    }
}

def installed() {
    log.info "${device.displayName}.installed()"
    
    if (settings.MQTTBroker?.trim())
         connect()
}

def updated() {
    if (logEnable) log.info "${device.displayName}.updated()"
    if (logEnable) runIn(900,logsOff)
    
    reconnect()
}

def uninstalled() {
    if (logEnable) log.info "Disconnecting from mqtt"
    interfaces.mqtt.disconnect()
    removeChildDevices()
}

def initialize() {
    if (logEnable) runIn(900,logsOff)
    
    state.connected = false
    updateOverrideSwitch("on")
    
    log.info "hub startup: connecting to mqtt in ${connectDelay} seconds"
    runIn(connectDelay.toInteger(), connect)
}

def reconnect() {
    disconnect()
    connect()
}

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
            if (logEnable) log.warn "parse: child presence sensor does not exist for ${macDNI}"
            macChild = addChildDevice("bujvary", "Virtual Presence with Timeout", macDNI, [label: "Vehicle Presence Sensor", isComponent: false])
        }
        
        if (logEnable) log.debug "parse: updating presence sensor data for " + macDNI
        macChild.parse(beacon.rssi)
    }
    
    if (!disableLastUpdated) {
        if (logEnable) log.debug "Sending event: name -> lastUpdated, value -> ${date.toString()}"
        sendEvent(name: "lastUpdated", value: "${date.toString()}", displayed: true)
    }
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
    log.info "connect() state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
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
            
            runIn(overrideEnableDelay.toInteger(), updateOverrideSwitch, [data: "off"])
        } catch(e) {
            if (logEnable) log.debug "Connect error: ${e.message}"
            runIn (5,"connect")
        }
    }
}

def disconnect() {
    log.info "disconnect() state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
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
    
    updateOverrideSwitch("on")
    
    log.info "disconnect() state.connected = ${state.connected}, state.reconnect = ${state.reconnect}"
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

def removeChildDevices() {
    log.info "Removing child devices"
    try {
        getChildDevices()?.each {
          try {
              deleteChildDevice(it.deviceNetworkId)
            } catch (e) {
                log.info "Error deleting ${it.deviceNetworkId}: ${e}"
            }
        }
    } catch (err) {
        log.info "Either no children exist or error finding child devices for some reason: ${err}"
    }
}

def updateOverrideSwitch(state) {
    log.info "Updating vehicle presence override switch state to ${state}"
    
    com.hubitat.app.DeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-switch")
    if(cd != null) {
        cd.parse([[name: "switch", value: state, descriptionText:"${cd.displayName} was turned ${state}", isStateChange: true]])
    }
}

void componentRefresh(cd) {
    log.warn "componentRefresh() not implemented"
}

void componentOn(cd){
    if (logEnable) log.debug "componentOn(${cd.displayName})"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
}

void componentOff(cd){
    if (logEnable) log.debug "componentOn(${cd.displayName})"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
