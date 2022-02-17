/**
 *  ****************  BLE Presence Gateway  ****************
 *
 *  Design Usage:
 *  This driver is used to get BLE beacon broadcast data from a BLE Wifi Gateway that posts data to a MQTT broker.
 *
 *  Copyright 2022 Brian Ujvary
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
 *  1.3.3 - Refactored health check logic once again to handle vehicle presence state switch properly
 *  1.3.2 - Refactored health check logic again to handle vehicle presence state switch properly
 *  1.3.1 - Refactored health check logic
 *  1.3.0 - Added health check to determine if BLE Gateway is alive
 *  1.2.0 - Removed reconnect() and added commands connect and disconnect
 *        - refactored connect/disconnect logic to prevent an infinite loop when the MQTT broker is down
 *        - added code back to create the generic component switch for vehicle presence override
 *  1.1.1 - Set state variables on initialize()
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
        command "connect"
        command "disconnect"
    }

    preferences {
        input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
        input name: "username", type: "text", title: "MQTT Username:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "password", type: "password", title: "MQTT Password:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "clientid", type: "text", title: "MQTT Client ID:", description: "<div><i>(blank if none)</i></div>", required: false, displayDuringSetup: true
        input name: "retryTime", type: "number", title: "MQTT Retry Connect Delay:", description: "<div><i>Number of seconds between connection retries if broker goes down</i></div>", defaultValue: 10, required: true
        input name: "connectDelay", type: "integer", title: "MQTT Startup Connect Delay:", description: "<div><i>On hub startup (in seconds)</i></div>", required: true, defaultValue: 60, displayDuringSetup: true
        input name: "overrideEnableDelay", type: "integer", title: "Vehicle Presence Override Enable Delay:", description: "<div><i>On MQTT connect (in seconds)</i></div>", required: true, defaultValue: 10, displayDuringSetup: true
        input name: "topicStatusSub", type: "text", title: "Status Topic (Subscribe):", description: "<div><i>Example Topic (topic/status/#)</i></div>", required: false, displayDuringSetup: true
        input name: "topicActionPublish", type: "text", title: "Action Topic (Publish):", description: "<div><i>Example Topic (topic/action/#)</i></div>", required: false, displayDuringSetup: true
        input name: "topicActionResponseSub", type: "text", title: "Action Response Topic (Subscribe):", description: "<div><i>Example Topic (topic/action/response/#)</i></div>", required: false, displayDuringSetup: true
        input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
        input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
        input name: "healthCheckInterval", type: "enum", title: "Health Check Interval", description: "<div><i>Number of seconds between health check messages sent to gateway</i></div>", options: ["10", "20", "30", "60"], required: true, defaultValue: "10", displayDuringSetup: true
        input name: "disableLastUpdated", type: "bool", title: "Disable LastUpdated Event", required: false, defaultValue: true, displayDuringSetup: true
        input name: "logEnable", type: "bool", title: "Enable logging", description: "<div><i>Automatically disables after 15 minutes</i></div>", required: true, defaultValue: true
    }
}

def installed() {
    if (logEnable) log.debug "installed()..."

    if (!getChildDevice("${device.deviceNetworkId}-switch")) {
        addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-switch", [completedSetup: true, label: "Vehicle Presence Override", isComponent: true])
    }
    
    if (settings.MQTTBroker?.trim())
         connect()
}

def updated() {
    if (logEnable) log.info "updated()..."
    
    if (logEnable) runIn(900,logsOff)
    
    unschedule()
    disconnect()
    connect()
    
    schedule("0/${settings.healthCheckInterval.toInteger()} * * * * ?", healthCheck)
}

def uninstalled() {
    if (logEnable) log.debug "uninstalled()..."
    
    if (logEnable) log.info "Disconnecting from mqtt"
    interfaces.mqtt.disconnect()
    removeChildDevices()
}

def initialize() {
    if (logEnable) log.debug "initialize()..."
    
    if (logEnable) runIn(900,logsOff) 
    unschedule()    
    updateOverrideSwitch("on")
    state.gatewayIsAlive = false
    
    log.info "hub startup: connecting to mqtt in ${connectDelay} seconds"
    runIn(connectDelay.toInteger(), connect)
    
    schedule("0/${settings.healthCheckInterval.toInteger()} * * * * ?", healthCheck)
}

def parse(String description) {
    if (logEnable) log.debug "parse()..."
    
    Date date = new Date();
    
    topic = interfaces.mqtt.parseMessage(description).topic
    topic = topic.substring(topic.lastIndexOf("/") + 1)
    
    payload = interfaces.mqtt.parseMessage(description).payload
    payloadJson = parseJson(payload)
    
    if (logEnable) log.debug "Topic: ${topic}"
    if (logEnable) log.debug "Payload: ${payload}"
    if (logEnable) log.debug "payloadJson: ${payloadJson}"
    
    if (topic == "status") {
        payloadJson.each { beacon ->
            if (logEnable) log.debug "mac: ${beacon.mac}"

            def macDNI = "ble:" + beacon.mac
            def macChild = getChildDevice(macDNI)
            if (macChild == null) {
                if (logEnable) log.warn "parse: child presence sensor does not exist for ${macDNI}"
                macChild = addChildDevice("bujvary", "Virtual Presence with Timeout", macDNI, [label: "Vehicle Presence Sensor", isComponent: true])
            }
        
            if (logEnable) log.debug "parse: updating presence sensor data for " + macDNI
            macChild.parse(beacon.rssi)
        }
    
        if (!disableLastUpdated) {
            if (logEnable) log.debug "Sending event: name -> lastUpdated, value -> ${date.toString()}"
            sendEvent(name: "lastUpdated", value: "${date.toString()}", displayed: true)
        }
    }
    
    if (topic == "response") {
        if (payloadJson.code == 200 && payloadJson.message == "success" && payloadJson.requestId == state.lastHealthCheck.toString()) {
            processHealthCheck()
        }
    }
}

def publishMsg(String s) {
    if (logEnable) log.debug "publishMsg()..."
    
    if (settings?.retained==null) settings?.retained=false
    if (settings?.QOS==null) setting?.QOS="1"
    
    if (logEnable) log.debug "Sent this: ${s} to ${settings?.topicActionPublish} - QOS Value: ${settings?.QOS.toInteger()} - Retained: ${settings?.retained}"
    interfaces.mqtt.publish(settings?.topicActionPublish, s, settings?.QOS.toInteger(), settings?.retained)
}

def connect() {
    if (logEnable) log.debug "connect()..."
    
    try {
        mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
        interfaces.mqtt.connect(mqttbroker, settings?.clientid, settings?.username,settings?.password)
        
        pauseExecution(1000)
        if (interfaces.mqtt.isConnected()) {
            connectionEstablished()
        }
        else {
            log.error ("MQTT client failed to connect to broker")
        }
        
    } catch(e) {
        log.error "MQTT initialize error: ${e.message}"
    }
}

def disconnect() {
    if (logEnable) log.debug "disconnect()..."
    
    if (interfaces.mqtt.isConnected()) {
        try {
            if (logEnable) log.debug "Attempting to disconnect from MQTT broker"
            interfaces.mqtt.disconnect()
        } catch (e) {
            log.error "Error disconnecting: ${e.message}"
        }
    }
    else {
        log.warn "MQTT client is already disconnected from broker"
    }
    
    sendEvent(name: "connection", value: "disconnected")
    updateOverrideSwitch("on")
}

void mqttClientStatus(String message) {
    if (logEnable) log.debug "mqttClientStatus()..."
    
    if (message.startsWith("Error:")) {
        log.error "MQTT client message - ${message}"
    }
    else {
        log.info "MQTT client message - ${message}"
    }

    if (message.contains ("Connection lost")) {
        connectionLost()
    }
}

void connectionEstablished() {
    if (logEnable) log.debug "connectionEstablished()..."
    
    interfaces.mqtt.subscribe(settings?.topicStatusSub)
    interfaces.mqtt.subscribe(settings?.topicActionResponseSub)
    sendEvent(name: "connection", value: "connected")    
    runIn(overrideEnableDelay.toInteger(), updateOverrideSwitch, [data: "off"])
}

void connectionLost() {
    if (logEnable) log.debug "connectionLost()..."
    
    disconnect()
    
    while (!interfaces.mqtt.isConnected()) {
        log.warn "Connection lost attempting to reconnect..."
        connect()
        pauseExecution(retryTime * 1000)
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

def healthCheck() {
    if (logEnable) log.debug "healthCheck()..."

    if (state.gatewayIsAlive == false) {
        def timeDiff = (now() - state.lastHealthCheck ?: 0) / 1000
        log.warn "Did not receive heartbeat response from BLE Gateway (timeDiff = ${timeDiff})"
        updateOverrideSwitch("on")
    }
    else {
        state.gatewayIsAlive = false
        state.lastHealthCheck = now()
        def actionJson = '{"action": "heartBeat","requestId": "' + state.lastHealthCheck +'"}'
        publishMsg(actionJson)
    }
}

def processHealthCheck() {
    if (logEnable) log.debug "processHealthCheck()..."

    state.gatewayIsAlive = true
    updateOverrideSwitch("off")
}

def updateOverrideSwitch(value) {
    if (logEnable) log.debug "updateOverrideSwitch(${value})..."

    com.hubitat.app.DeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-switch")
    switchValue = cd?.currentValue("switch")

    if (switchValue != value)
    {
        log.info "Updating vehicle presence override switch state to ${value}"
        cd.parse([[name: "switch", value: value, descriptionText:"${cd.displayName} was turned ${value}", isStateChange: true]])
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
