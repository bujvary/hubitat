/**
 *  Hunter Douglas PowerView Gen3
 *
 *  Copyright 2017 Chris Lang
 *
 *  Ported to Hubitat by Brian Ujvary
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change Log:
 *    10/08/2022 v0.5 - Added Server Sent Event capability
 *    10/06/2022 v0.4 - Version number update only
 *    10/04/2022 v0.3 - Removed hardcoded testing port for HD Powerview URL
 *    10/04/2022 v0.2 - Added checks for HTTP status codes in callback functions
 *                    - Changed call to trigger scene from GET to PUT
 *    10/03/2022 v0.1 - Initial release
 *
 */

import com.hubitat.app.DeviceWrapper
import groovy.transform.Field

@Field static String htmlTab = "&nbsp;&nbsp;&nbsp;&nbsp;"
@Field static String ShadeSseDni = "PowerView-Shade-SSE-1"

definition(
    name: "Hunter Douglas PowerView Gen3",
    namespace: "hdpowerview",
    author: "Brian Ujvary",
    description: "Provides control of Hunter Douglas shades and scenes via the PowerView Gen3 Gateway.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/apps/hunter-douglas-powerview-gen3.groovy"
)


preferences {
    section("Title") {
        page(name: "mainPage")
        page(name: "devicesPage")
        page(name: "notificationsPage")
        page(name: "roomsPage")
    }
}

/*
 * Pages
 */
def mainPage() {
    def setupComplete = !!atomicState?.shades || !!atomicState?.scenes
    def pageProperties = [
        name: "mainPage",
        title: "",
        install: setupComplete,
        uninstall: atomicState?.installed
    ]

    // TO DO: REMOVE THIS LINE WHEN DONE
    logEnable = true
    
    if (state?.shadeEventStreamStatus == null)
        state.shadeEventStreamStatus = false
    
    if (atomicState?.gettingFirmwareVer == null)
        atomicState.gettingFirmwareVer = true
    
    if(atomicState?.gettingFirmwareVerError == null)
        atomicState.gettingFirmwareVerError = false
    
    if (logEnable) log.debug "atomicState?.gettingFirmwareVer = ${atomicState?.gettingFirmwareVer}"
    if (atomicState?.gettingFirmwareVer && settings?.powerviewIPAddress) {
        pageProperties["refreshInterval"] = 1
        getFirmwareVer()
    }

    return dynamicPage(pageProperties) {
        section("<big><b>PowerView Gateway</b></big>") {
            input("powerviewIPAddress", "text", title: "IP Address", defaultValue: "", description: "(ie. 192.168.1.10)", required: true, submitOnChange: true)
        }
       
        if (settings?.powerviewIPAddress) {
            if (atomicState?.gettingFirmwareVer) {
                section("Getting firmware version...") {
                    paragraph "Please wait..."
                }
            }
            else {
                if (atomicState?.gettingFirmwareVerError == false) {
                    section("<big><b>Firmware Version</b></big>") {
                        paragraph "${htmlTab}Name: ${state.fwName}</br>${htmlTab}Revision: ${state.fwRevision}</br>${htmlTab}SubRevision: ${state.fwSubRevision}</br>${htmlTab}Build: ${state.fwBuild}"
                    }
                        if (settings.useShadeEventStream == true) {
                        section("<big><b>Event Stream Status</b></big>") {
                            def shadeSseStatus = getShadeEventStreamStatus()
                            paragraph "${htmlTab}Shade Event Stream: ${shadeSseStatus}"
                        }
                    }
                    section("<big><b>Devices & Scenes</b></big>") {
                        def description = (atomicState?.deviceData) ? "Click to modify" : "Click to configure";
                        href "devicesPage", title: "Manage Devices", description: description, state: "complete"
                        atomicState?.loadingDevices = false
                    }
                    section("<big><b>Polling</b></big>") {
                        input("enableShadePoll", "bool", title: "Enable periodic polling of shade devices", required: false, defaultValue: true)
                        input("shadePollInterval", 'enum', title: "Shade Position Polling Interval", required: true, defaultValue: shadePollIntervalSetting, options: getPollIntervals())
                    }
                    section("<big><b>Event Streaming</b></big>") {
                        input("useShadeEventStream", "bool", title: "Enable server sent events for shade devices", required: false, defaultValue: false)
                    }
                    section("<big><b>Notifications</b></big>") {
                        href "notificationsPage", title: "Text Notifications", description: "Click here for Options", state: "complete"
                        atomicState?.loadingDevices = false
                    }
                    section("<big><b>Logging</b></big>") {
                        input("logEnable", "bool", title: "Enable debug logging", required: false, defaultValue: true)
                        paragraph "<i>Automatically disables after 15 minutes</i>"
                    }
                }
                else {
                    section("<big><b>Firmware Version</b></big>") {
                        paragraph "${htmlTab}Failed to get firmware version from Powerview gateway"
                    }                    
                }
            }
        }
    }
}

def devicesPage() {
    def pageProperties = [
        name: "devicesPage",
        title: "<b>Manage Devices</b>"
    ]

    if (logEnable) log.debug "atomicState?.loadingDevices = ${atomicState?.loadingDevices}"
    if (logEnable) log.debug "atomicState?.gettingRooms = ${atomicState?.gettingRooms}"
    if (logEnable) log.debug "atomicState?.gettingShades = ${atomicState?.gettingShades}"
    if (logEnable) log.debug "atomicState?.gettingScenes = ${atomicState?.gettingScenes}"
    
    if (atomicState?.loadingDevices) {
        if (atomicState?.gettingRooms)
           getRooms()
        else if (atomicState?.gettingShades)
            getShades()
        else if (atomicState?.gettingScenes)
            getScenes()
    } 
    else {
        atomicState?.loadingDevices = true
        atomicState?.gettingRooms = true
        atomicState?.gettingShades = false
        atomicState?.gettingScenes = false
    }
    
    if (logEnable) log.debug "atomicState?.deviceData = ${atomicState?.deviceData}"

    if (atomicState?.gettingShades || atomicState?.gettingScenes || atomicState?.gettingRooms) {
        pageProperties["refreshInterval"] = 1
        return dynamicPage(pageProperties) {
            section("Discovering Devices...") {
                paragraph "Please wait..."
            }
        }
    }

    return dynamicPage(pageProperties) {
        section("Rooms") {
            paragraph "NOTE: If you changed the name of a room in the Powerview app, you will need to click on \"Manage Rooms\" for the associated device label to be changed."
            href "roomsPage", title: "Manage Rooms", description: "Click to configure open/close scenes for each room", state: "complete"
        }
        section("Shades") {
            input("syncShades", "bool", title: "Automatically sync all shades", required: false, defaultValue: true, submitOnChange: true)
            if (settings?.syncShades == true || settings?.syncShades == null) {
                def shadesDesc = "None"
                if (atomicState?.deviceData?.shades)
                    shadesDesc = atomicState?.deviceData?.shades.values().join(", ")
                paragraph "The following shades will be added as devices: ${shadesDesc}"
                atomicState?.shades = atomicState?.deviceData?.shades
            }
            else {
                def shadesList = getDiscoveredShadeList()
                input(name: "shades", title: "Shades", type: "enum", required: false, multiple: true, submitOnChange: true, options: shadesList)
                atomicState?.shades = getSelectedShades(settings?.shades)
                if (logEnable) log.debug "shades: ${settings?.shades}"
            }
        }
        section("Scenes") {
            input("syncScenes", "bool", title: "Automatically sync all scenes", required: false, defaultValue: true, submitOnChange: true)
            if (settings?.syncScenes == true || settings?.syncScenes == null) {
                def scenesDesc = "None"
                if (atomicState?.deviceData?.scenes)
                    scenesDesc = atomicState?.deviceData?.scenes.values().join(", ")
                paragraph "The following scenes will be added as devices: ${scenesDesc}"
                atomicState?.scenes = atomicState?.deviceData?.scenes
            }
            else {
                def scenesList = getDiscoveredSceneList()
                input(name: "scenes", title: "Scenes", type: "enum", required: false, multiple: true, submitOnChange: true, options: scenesList)
                atomicState?.scenes = getSelectedScenes(settings?.scenes)
                if (logEnable) log.debug "scenes: ${settings?.scenes}"
            }
        }
    }
}

def notificationsPage() {
    def pageProperties = [
        name: "notificationsPage",
        title: "<b>Notifications</b>"
    ]

	dynamicPage(pageProperties) {
		section() {
			paragraph "Send push notification once per day when shade battery wand is low (as defined by Hunter Douglas)."

			input "shadeBatteryLowDevices", "capability.notification",
				title: "<b>Send notification to devices(s):</b>",
				multiple: true,
				required: false
		}
    }
}
    
def roomsPage() {
    def pageProperties = [
        name: "roomsPage",
        title: "<b>Manage Rooms</b>"
    ]

    dynamicPage(pageProperties) {
        section {
            paragraph("Configure scenes to open or close the blinds in each room. A virtual device will be created for each room so configured.")
        }
        def rooms = [:]
        if (atomicState?.deviceData.rooms) {
            atomicState?.deviceData.rooms.collect { id, name ->
                section(name) {
                    def openSetting = "room" + id + "Open"
                    def closeSetting = "room" + id + "Close"
                    def description
                    if (settings[openSetting] && settings[closeSetting]) {
                        description = "Blinds in this room will open and close via the configured scenes."
                    }
                    else if (settings[openSetting]) {
                        description = "Blinds in this room will open via the configured scene, but not close."
                    }
                    else if (settings[closeSetting]) {
                        description = "Blinds in this room will close via the configured scene, but not open."
                    }
                    else {
                        description = "No virtual device will be created for this room because neither open nor close scenes are configured."
                    }
                    paragraph(description)

                    // TODO limit to scenes for this room or multi-room scenes
                    def scenesList = getDiscoveredSceneList()
                    input(name: openSetting, title: "Open", type: "enum", required: false, multiple: false, submitOnChange: true, options: scenesList)
                    input(name: closeSetting, title: "Close", type: "enum", required: false, multiple: false, submitOnChange: true, options: scenesList)

                    rooms[id] = [
                        name: name,
                        openScene: settings[openSetting],
                        closeScene: settings[closeSetting],
                    ]
                }
            }
        } 
        else {
            section() {
                paragraph("No rooms discovered")
            }
        }
        
        atomicState?.rooms = rooms
        if (logEnable) log.debug "atomicState?.rooms = ${atomicState?.rooms}"
    }
}

/*
 * Service Manager lifecycle
 */
def installed() {
    if (logEnable) log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    if (logEnable) log.debug "Updated with settings: ${settings}"

    initialize()
}

def uninstalled() {
    removeDevices()
    unschedule()
}

def initialize() {
    atomicState?.installed = true
    addDevices()
    unschedule()
    
    pollDevices(true)

    if (logEnable) log.debug "Configuring shade polling for every ${shadePollIntervalSetting} ${shadePollIntervalSetting == 1 ? 'minute' : 'minutes'}"

    switch (shadePollIntervalSetting) {
      case 1:
        if (logEnable) log.debug "runEvery1Minute"
        runEvery1Minute("pollDevices")
        break
      case 5:
        if (logEnable) log.debug "runEvery5Minutes"
        runEvery5Minutes("pollDevices")
        break
      case 10:
        if (logEnable) log.debug "runEvery10Minutes"
        runEvery10Minutes("pollDevices")
        break
      case 15:
        if (logEnable) log.debug "runEvery15Minutes"
        runEvery15Minutes("pollDevices")
        break
      case 30:
        if (logEnable) log.debug "runEvery30Minutes"
        runEvery30Minutes("pollDevices")
        break
      case 60:
        if (logEnable) log.debug "runEvery1Hour"
        runEvery1Hour("pollDevices")
        break
      default:
        if (logEnable) log.debug "DEFAULT: runEvery5Minutes"
        runEvery5Minutes("pollDevices")
    }
 
    DeviceWrapper shade_sse = getChildDevice(ShadeSseDni)
    if (settings.useShadeEventStream == true) {
        shade_sse?.connectEventStream()
    }
    else {
        shade_sse?.disconnectEventStream()
    }
    
    if (logEnable) runIn(900, logsOff)
}

def logsOff() {
    log.warn "Debug logging disabled."
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def addDevices() {
    if (logEnable) log.debug "In addDevices()"

    if (atomicState?.rooms) {
        atomicState?.rooms?.collect { id, room ->
            if (logEnable) log.debug "checking room ${id}"
            def dni = roomIdToDni(id)
            def child = getChildDevice(dni)
            if (!child) {
                if (room.openScene || room.closeScene) {
                    child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Room Gen3", dni, [label: getRoomLabel(room.name)])
                    if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
                }
            }
            else {
                def childLabel = child.getLabel()
                def roomName = getRoomLabel(room.name)
                if (childLabel != roomName) {
                    child.setLabel(roomName)
                   if (logEnable) log.debug "Changed room device label from '${childLabel}' to '${roomName}'"
                }
            }
        }
    }
    
    if (atomicState?.shades) {
        atomicState?.shades?.collect { id, name ->
            def dni = shadeIdToDni(id)
            def child = getChildDevice(dni)
            if (!child) {
                child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Shade Gen3", dni, [label: name])
                if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
            }
            else {
                def childLabel = child.getLabel()
                if (childLabel != name) {
                    child.setLabel(name)
                    if (logEnable) log.debug "Changed shade device label from '${childLabel}' to '${name}'"
                }
            }
        }
    }
    
    if (atomicState?.scenes) {
        atomicState?.scenes?.collect { id, name ->
            def dni = sceneIdToDni(id)
            def child = getChildDevice(dni)
            if (!child) {
                child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Scene Gen3", dni, [label: name])
                if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
            }
            else {
                def childLabel = child.getLabel()
                if (childLabel != name) {
                    child.setLabel(name)
                    if (logEnable) log.debug "Changed scenes device label from '${childLabel}' to '${name}'"
                }
            }
        }
    }
    
    if (!getChildDevice(ShadeSseDni)) {
        def name = "Hunter Douglas PowerView Shade SSE Gen3"
        child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Shade SSE Gen3", ShadeSseDni, [label: name, isComponent: true])
        if (logEnable) log.debug "Created child '${child}' with dni ${ShadeSseDni}"
    }
}

def removeDevices() {
    if (logEnable) log.debug "In removeDevices()"

    try {
        getChildDevices()?.each {
            try {
                if (logEnable) log.debug "Deleting device ${it.deviceNetworkId}"
                deleteChildDevice(it.deviceNetworkId)
            } catch (e) {
                if (logEnable) log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
            }
        }
    } catch (err) {
        if (logEnable) log.debug "Either no children exist or error finding child devices for some reason: ${err}"
    }
}

def pollDevices(firstPoll = false) {
    def now = now()
    def runDelay = 1

    if (enableShadePoll || firstPoll) {
        getShadeDevices().eachWithIndex { device, index ->
            if (device != null) {
                def shadeId = dniToShadeId(device.deviceNetworkId)
            
                if (logEnable) log.debug "Running pollShadeDelayed() with runDelay = ${runDelay} for shade ${shadeId} (index = ${index})"
            
                runIn(runDelay, "pollShadeDelayed", [overwrite: false, data: [shadeId: shadeId]])
                runDelay += 5
            }
            else {
                if (logEnable) log.debug "Got null shade device, index ${index}"
            }
        }
    }
}

def pollShadeDelayed(data) {
    if (logEnable) log.debug "pollShadeDelayed: data: ${data}"
    pollShadeId(data.shadeId)
}

/*
 * Device management
 */

def getRoomLabel(roomName) {
    return "${roomName} Blinds"
}

def getRoomDniPrefix() {
    return "PowerView-Room-"
}

def getSceneDniPrefix() {
    return "PowerView-Scene-"
}

def getShadeDniPrefix() {
    return "PowerView-Shade-"
}

def roomIdToDni(id) {
    return "${getRoomDniPrefix()}${id}"
}

def dniToRoomId(dni) {
    def prefix = getRoomDniPrefix()
    return dni.startsWith(prefix) ? dni.replace(prefix, "") : null
}

def sceneIdToDni(id) {
    return "${getSceneDniPrefix()}${id}"
}

def dniToSceneId(dni) {
    def prefix = getSceneDniPrefix()
    return dni.startsWith(prefix) ? dni.replace(prefix, "") : null
}

def shadeIdToDni(id) {
    return "${getShadeDniPrefix()}${id}"
}

def dniToShadeId(dni) {
    def prefix = getShadeDniPrefix()
    return dni.startsWith(prefix) ? dni.replace(prefix, "") : null
}

def getSceneDevices() {
    return atomicState?.scenes?.keySet().collect {
        getChildDevice(sceneIdToDni(it))
    }
}

def getShadeDevice(shadeId) {
    return getChildDevice(shadeIdToDni(shadeId))
}

def getShadeDevices() {
    return atomicState?.shades?.keySet().collect {
        getChildDevice(shadeIdToDni(it))
    }
}

// data can contain 'shades', 'scenes' and/or 'rooms' -- only deviceData for specified device types is updated
def updateDeviceDataState(data) {
    def deviceData = atomicState?.deviceData ?: [:]

    if (data?.rooms) {
        deviceData["rooms"] = data?.rooms
    }
    if (data?.scenes) {
        deviceData["scenes"] = data?.scenes
    }
    if (data?.shades) {
        deviceData["shades"] = data?.shades
    }

    atomicState?.deviceData = deviceData
    if (logEnable) log.debug "updateDeviceData: atomicState.deviceData: ${atomicState?.deviceData}"
}

def getSelectedShades(Collection selectedShadeIDs) {
    return getSelectedDevices(atomicState?.deviceData?.shades, selectedShadeIDs)
}

def getSelectedScenes(Collection selectedSceneIDs) {
    return getSelectedDevices(atomicState?.deviceData?.scenes, selectedSceneIDs)
}

def getSelectedDevices(Map devices, Collection selectedDeviceIDs) {
    if (!selectedDeviceIDs) {
        return [:]
    }
    return devices?.findAll {
        selectedDeviceIDs.contains(it.key)
    }
}

def getDiscoveredShadeList() {
    def ret = [:]
    atomicState?.deviceData?.shades.each { shade ->
            ret[shade.key] = shade.value
    }
    return ret
}

def getDiscoveredSceneList() {
    def ret = [:]
    atomicState?.deviceData?.scenes.each { scene ->
            ret[scene.key] = scene.value
    }
    return ret
}

/*
 * PowerView API
 */

// FIRMWARE
def getFirmwareVer() {
    callPowerView("gateway/", firmwareVerCallback)
    atomicState?.gettingFirmwareVer = true
}

void firmwareVerCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered firmwareVerCallback()..."
    if (logEnable) log.debug "status: ${hubResponse.status}"
    if (logEnable) log.debug "json: ${hubResponse.json}"

    if (hubResponse.status == 200) {
        state.fwName = hubResponse.json.config.firmware.mainProcessor.name
        state.fwRevision = hubResponse.json.config.firmware.mainProcessor.revision
        state.fwSubRevision = hubResponse.json.config.firmware.mainProcessor.subRevision
        state.fwBuild = hubResponse.json.config.firmware.mainProcessor.build
    }
    else {
        log.error "firmwareVerCallback() Failed to get firmware information"
        atomicState?.gettingFirmwareVerError = true
    }
    
    atomicState?.gettingFirmwareVer = false
}

// ROOMS

def getRooms() {
    if (logEnable) log.debug "Entered getRooms()..."
    callPowerView("home/rooms", roomsCallback)
}

def openRoom(roomDevice) {
    if (logEnable) log.debug "openRoom: roomDevice = ${roomDevice}"

    def roomId = dniToRoomId(roomDevice.deviceNetworkId)
    def sceneId = atomicState?.rooms[roomId]?.openScene
    if (sceneId) {
        triggerScene(sceneId)
    }
    else {
        log.info "no open scene configured for room ${roomId}"
    }
}

def closeRoom(roomDevice) {
    if (logEnable) log.debug "closeRoom: roomDevice = ${roomDevice}"

    def roomId = dniToRoomId(roomDevice.deviceNetworkId)
    def sceneId = atomicState?.rooms[roomId]?.closeScene
    if (sceneId) {
        triggerScene(sceneId)
    }
    else {
        log.info "no close scene configured for room ${roomId}"
    }
}

void roomsCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered roomsCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def rooms = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.each { room ->
            def name = new String(room.name.decodeBase64())
            rooms[room.id] = name
            if (logEnable) log.debug "room: ID = ${room.id}, name = ${name}"
        }
    }
    else {
        log.error "roomsCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
    
    updateDeviceDataState([rooms: rooms])
    
    atomicState?.gettingRooms = false
    atomicState?.gettingShades = true
}

// SCENES

def getScenes() {
    if (logEnable) log.debug "Entered getScenes()..."
    callPowerView("home/scenes", scenesCallback)
}

def triggerSceneFromDevice(sceneDevice) {
    def sceneId = dniToSceneId(sceneDevice.deviceNetworkId)
    triggerScene(sceneId)
}

def triggerScene(sceneId) {
    callPowerView("home/scenes/${sceneId}/activate", triggerSceneCallback, null, "PUT")
}

void scenesCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered scenesCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def scenes = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.each {scene ->
            def name = new String(scene.name.decodeBase64())
            scenes[scene.id] = name
            if (logEnable) log.debug "scene: ID = ${scene.id}, name = ${name}"
        }
    }
    else {
        log.error "scenesCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
    
    updateDeviceDataState([scenes: scenes])
    
    atomicState?.gettingScenes = false
}

def triggerSceneCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered triggerScenesCallback()..."

    if (hubResponse.status == 200) {
        runIn(15, pollDevices)
    }
    else {
        log.error "triggerSceneCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
}

// SHADES 

def getShades() {
    if (logEnable) log.debug "Entered getShades()..."
    callPowerView("home/shades", shadesCallback)
}

def pollShade(shadeDevice) {
    if (logEnable) log.debug "pollShade: shadeDevice = ${shadeDevice}"
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    pollShadeId(shadeId)
}

def pollShadeId(shadeId) {
    if (logEnable) log.debug "pollShadeId: shadeId = ${shadeId}"
    callPowerView("home/shades/${shadeId}", shadePollCallback)
}

def calibrateShade(shadeDevice) {
    if (logEnable) log.debug "calibrateShade: shadeDevice = ${shadeDevice}"
    
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    def json = new groovy.json.JsonBuilder([motion: "calibrate"])
    callPowerView("home/shades/${shadeId}/motion", setPositionCallback, null, "PUT", json.toString())
}

def jogShade(shadeDevice) {
    if (logEnable) log.debug "jogShade: shadeDevice = ${shadeDevice}"
    
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    def json = new groovy.json.JsonBuilder([motion: "jog"])
    callPowerView("home/shades/${shadeId}/motion", setPositionCallback, null, "PUT", json.toString())
}

def stopShade(shadeDevice) {
    if (logEnable) log.debug "stopShade: shadeDevice = ${shadeDevice}"

    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    callPowerView("home/shades/stop", setPositionCallback, [ids: shadeId], "PUT", null)
}

def setPosition(shadeDevice, positions) {
    if (logEnable) log.debug "setPosition: shadeDevice = ${shadeDevice}, positions = ${positions}"

    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    def shadePositions = [:]

    if (positions?.containsKey("primary")) {
        shadePositions["primary"] = positions.primary / 100
    }

    if (positions?.containsKey("secondary")) {
        shadePositions["secondary"] = positions.secondary / 100
    }

    if (positions?.containsKey("tilt")) {
        def childDevice = getShadeDevice(dniToShadeId(shadeDevice.deviceNetworkId))
        shadePositions["tilt"] = positions.tilt / 100
    }
    
    def json = new groovy.json.JsonBuilder([positions: shadePositions])
    callPowerView("home/shades/positions", setPositionCallback, [ids: shadeId], "PUT", json.toString())
}

def moveShade(shadeDevice, movementInfo) {
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)

    def body = [:]
    body["shade"] = movementInfo

    def json = new groovy.json.JsonBuilder(body)
    callPowerView("home/shades/${shadeId}/motion", setPositionCallback, null, "PUT", json.toString())
}

void shadePollCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered shadePollCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    if (hubResponse.status == 200) {
        def shade = hubResponse.json
        def childDevice = getShadeDevice(shade.id)

        if (logEnable) log.debug "shadePollCallback for shade id ${shade.id}, calling device ${childDevice}"
            childDevice.handleEvent(shade)
    }
    else {
        log.error "shadePollCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
}

def shadesPollCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "In shadesPollCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    if (hubResponse.status == 200) {
        hubResponse.json.each { shade ->
            def childDevice = getShadeDevice(shade.id)
            
            if (childDevice != null)
                childDevice.handleEvent(shade)
        }
    }
    else {
        log.error "shadesPollCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
}

void setPositionCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered setPositionCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    if (hubResponse.status == 200) {
        hubResponse.json.responses.each { response ->
            def childDevice = getShadeDevice(response.id)
            if (logEnable) log.debug "setPositionCallback for shadeId ${response.id}, device ${childDevice}"
        }
    }
    else {
        log.error "setPositionCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
}


void shadesCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered shadesCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def shades = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.each { shade ->
            def name = shade.name ? new String(shade.name.decodeBase64()) : "Shade ID ${shade.id}"
            shades[shade.id] = shade.ptName
            if (logEnable) log.debug "shade: ID = ${shade.id}, name = ${name}"
        }
    }
    else {
        log.error "shadesCallback() HTTP Error (${hubResponse.status}): ${hubResponse.json.errMsg}"
    }
    
    updateDeviceDataState([shades: shades])
    
    atomicState?.gettingShades = false
    atomicState?.gettingScenes = true
}

def sendBatteryLowNotification(shadeDevice) {
    if (logEnable) log.debug "sendBatteryLowNotification: shadeDevice = ${shadeDevice}"
	
	def pushDevices = settings?.shadeBatteryLowDevices	
	if (pushDevices) {
		String msg = "${shadeDevice?.displayName} battery wand is low"
		if (logEnable) log.debug "${shadeDevice?.displayName} - Sending push notification: ${msg}"
		pushDevices*.deviceNotification(msg)
	}
}

// CORE API

def callPowerView(String path, callback, Map query = null, String method = "GET", String body = null) {
    def host = "${settings?.powerviewIPAddress}:80"
    def fullPath = "/${path}"
    
    if (logEnable) log.debug "callPowerView: url = 'http://${host}${fullPath}', method = '${method}', body = '${body}', query = ${query}, callback = ${callback}"

    def headers = [
        'HOST': host,
        'Content-Type': 'application/json'
    ]

    def hubAction = new hubitat.device.HubAction(
        method: method,
        path: fullPath,
        headers: headers,
        query: query,
        body: body,
        null,
        [callback: callback]
    )

    if (logEnable) log.debug "Sending HubAction: ${hubAction}"

    sendHubCommand(hubAction)
}

// SHADE SSE FUNCTIONS
Boolean getShadeEventStreamEnabledSetting() {
    if (logEnable) log.debug "getShadeEventStreamEnabledSetting()"
    
    return (settings.useShadeEventStream == true) ? true : false
}

void setShadeEventStreamStatus(Boolean isConnected) {
    if (logEnable) log.debug "setShadeEventStreamStatus($isConnected)"

    state.shadeEventStreamStatus = isConnected
}

String getShadeEventStreamStatus() {
    if (logEnable) log.debug "getShadeEventStreamStatus()"

    return (state.shadeEventStreamStatus == true) ? "<span style='color:green'>Connected</span>" : "<span style='color:red'>Disconnected</span>"
}

// UTILITY FUNCTIONS
def getGatewayIP() {
    return settings?.powerviewIPAddress
}

def getPollIntervals() {
    return [["1":"1 Minute"],["5":"5 Minutes [DEFAULT]"],["10":"10 Minutes"],["15":"15 Minutes"],["30":"30 Minutes"],["60":"60 Minutes"]]
}

Integer getShadePollIntervalSetting() {
	return safeToInt((settings ? settings["shadePollInterval"] : null), 5)
}

Integer safeToInt(val, Integer defaultVal=0) {
	if ("${val}"?.isInteger()) {
		return "${val}".toInteger()
	}
	else if ("${val}".isDouble()) {
		return "${val}".toDouble()?.round()
	}
	else {
		return  defaultVal
	}
}
