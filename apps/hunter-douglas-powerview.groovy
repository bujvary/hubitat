/**
 *  Hunter Douglas PowerView
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
 *    01/25/2022 v2.6.0 - Reworked device discovery so it's done serially to prevent overloading the hub
 *                      - Added additional debugging
 *    01/25/2022 v2.5.0 - Added option to control forced refresh polling
 *                      - Added option for shade position polling interval
 *                      - Added option for battery level polling interval
 *                      - Changed option disablePoll to enableShadePoll
 *                      - Modified formatting of app pages
 *    01/24/2022 v2.4.0 - Reverted back to individual device polling 
 *                      - Removed option to control forced refresh polling
 *                        NOTE: Going back to individual shade polling using the refresh query string
 *                        was done to ensure an accurate Hub view of shade position since shades can be
 *                        moved without the Powerview hub's knowledge i.e. by pressing the motion button
 *                        on the side of the shade or by using a PowerView Motorization handheld remote
 *                        control device
 *    01/21/2022 v2.3.0 - Changed to group polling of devices instead of individual device polling
 *                      - Added option to control polling of repeaters
 *                      - Added option to control forced refresh polling 
 *    01/20/2022 v2.2.0 - Added stopShade()
 *                      - Removed unnecessary calls to unsubscribe()
 *    01/19/2022 v2.1.0 - General release with shade tilt capability
 *    01/12/2022 v2.0.1 - Fixed issues with tilt capability
 *    01/06/2021 v2.0.0 - Added tilt capability based on shade capabilities
 *    01/06/2022 v1.5 - Allow user to complete setup without selecting shades (i.e. scenes only)
 *    10/30/2021 v1.4 - Moved check for the last time the low battery notification was sent to shade driver
 *    10/26/2021 v1.3 - Added text notification option for low battery wand condition
 *                    - Fixed update battery level request interval calculation in pollDevices()
 *    10/08/2020 v1.2 - Added logic to update device labels if the device names changed in
 *                      the PowerView hub
 *    06/20/2020 v1.1 - Added retrieval of firmware information and display on main page
 *                    - Added logic to determine when all device discovery is complete
 *                    - Added logic to check that devices exist before creating device
 *                      descriptions on the devices page
 *                    - Added logic to check the HTTP status in the device discovery callbacks
 *    05/10/2020 v1.0 - Initial release
 *
 */

import groovy.transform.Field

@Field static String htmlTab = "&nbsp;&nbsp;&nbsp;&nbsp;"

definition(
    name: "Hunter Douglas PowerView",
    namespace: "hdpowerview",
    author: "Brian Ujvary",
    description: "Provides control of Hunter Douglas shades, scenes and repeaters via the PowerView hub.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/apps/hunter-douglas-powerview.groovy"
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

    if (atomicState?.gettingFirmwareVer == null)
        atomicState?.gettingFirmwareVer = true
    
    if (logEnable) log.debug "atomicState?.gettingFirmwareVer = ${atomicState?.gettingFirmwareVer}"
    if (atomicState?.gettingFirmwareVer && settings?.powerviewIPAddress) {
        pageProperties["refreshInterval"] = 1
        getFirmwareVer()
    }

    return dynamicPage(pageProperties) {
        section("<big><b>PowerView Hub</b></big>") {
            input("powerviewIPAddress", "text", title: "IP Address", defaultValue: "", description: "(ie. 192.168.1.10)", required: true, submitOnChange: true)
        }
       
        if (settings?.powerviewIPAddress) {
            if (atomicState?.gettingFirmwareVer) {
                section("Getting firmware version...") {
                    paragraph "Please wait..."
                }
            } else {
                section("<big><b>Firmware Version</b></big>") {
                    paragraph "${htmlTab}Name: ${state.fwName}</br>${htmlTab}Revision: ${state.fwRevision}</br>${htmlTab}SubRevision: ${state.fwSubRevision}</br>${htmlTab}Build: ${state.fwBuild}"
                }
                section("<big><b>Devices & Scenes</b></big>") {
                    def description = (atomicState?.deviceData) ? "Click to modify" : "Click to configure";
                    href "devicesPage", title: "Manage Devices", description: description, state: "complete"
                    atomicState?.loadingDevices = false
                }
                section("<big><b>Polling</b></big>") {
                    input("enableShadePoll", "bool", title: "Enable periodic polling of shade devices", required: false, defaultValue: true)
                    input("enableRepeaterPoll", "bool", title: "Enable periodic polling of repeater devices", required: false, defaultValue: false)
                    input("enableForcedUpdate", "bool", title: "Enable forced update of shade devices", required: false, defaultValue: true)
                    paragraph "<i>NOTE: If forced update of shade devices is NOT enabled the cached state of the shades will be retrieved from the Powerview Hub which may not match the actual state of the shades. Enabling forced update will ensure Hubitat will have the current state of the shades.</i>"
                    
                    input("shadePollInterval", 'enum', title: "Shade Position Polling Interval", required: true, defaultValue: shadePollIntervalSetting, options: getPollIntervals())
                    input("batteryPollInterval", 'number', title: "Battery Level Polling Interval (hours)", required: true, defaultValue: 1, range: "1..24")
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
    if (logEnable) log.debug "atomicState?.gettingRepeaters = ${atomicState?.gettingRepeaters}"
    
    if (atomicState?.loadingDevices) {
        if (atomicState?.gettingRooms)
           getRooms()
        else if (atomicState?.gettingShades)
            getShades()
        else if (atomicState?.gettingScenes)
            getScenes()
        else if (atomicState?.gettingRepeaters)
            getRepeaters()
    } else {
        atomicState?.loadingDevices = true
        atomicState?.gettingRooms = true
        atomicState?.gettingShades = false
        atomicState?.gettingScenes = false
        atomicState?.gettingRepeaters = false
    }
    
    if (logEnable) log.debug "atomicState?.deviceData = ${atomicState?.deviceData}"

    if (atomicState?.gettingShades || atomicState?.gettingScenes || atomicState?.gettingRooms || atomicState?.gettingRepeaters) {
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
            } else {
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
            } else {
                def scenesList = getDiscoveredSceneList()
                input(name: "scenes", title: "Scenes", type: "enum", required: false, multiple: true, submitOnChange: true, options: scenesList)
                atomicState?.scenes = getSelectedScenes(settings?.scenes)
                if (logEnable) log.debug "scenes: ${settings?.scenes}"
            }
        }
        section("Repeaters") {
            input("syncRepeaters", "bool", title: "Automatically sync all repeaters", required: false, defaultValue: true, submitOnChange: true)
            if (settings?.syncRepeaters == true || settings?.syncRepeaters == null) {
                def repeatersDesc = "None"
                if (atomicState?.deviceData?.repeaters)
                    repeatersDesc = atomicState?.deviceData?.repeaters.values().join(", ")
                paragraph "The following repeaters will be added as devices: ${repeatersDesc}"
                atomicState?.repeaters = atomicState?.deviceData?.repeaters
            } else {
                def repeatersList = getDiscoveredRepeaterList()
                input(name: "repeaters", title: "Repeaters", type: "enum", required: false, multiple: true, submitOnChange: true, options: repeatersList)
                atomicState?.repeaters = getSelectedRepeaters(settings?.repeaters)
                if (logEnable) log.debug "repeaters: ${settings?.repeaters}"
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
                    } else if (settings[openSetting]) {
                        description = "Blinds in this room will open via the configured scene, but not close."
                    } else if (settings[closeSetting]) {
                        description = "Blinds in this room will close via the configured scene, but not open."
                    } else {
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
        } else {
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
                    child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Room", dni, null, [label: getRoomLabel(room.name)])
                    if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
                }
            } else {
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
                child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Shade", dni, null, [label: name])
                if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
            } else {
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
                child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Scene", dni, null, [label: name])
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
    if (atomicState?.repeaters) {
        atomicState?.repeaters?.collect { id, name ->
            def dni = repeaterIdToDni(id)
            def child = getChildDevice(dni)
            if (!child) {
                child = addChildDevice("hdpowerview", "Hunter Douglas PowerView Repeater", dni, null, [label: name])
                if (logEnable) log.debug "Created child '${child}' with dni ${dni}"
            } else {
                def childLabel = child.getLabel()
                if (childLabel != name) {
                    child.setLabel(name)
                    if (logEnable) log.debug "Changed repeater device label from '${childLabel}' to '${name}'"
                }
            }
            
        }
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
    def updateBattery = false
    def runDelay = 1

    if (enableShadePoll || firstPoll) {
        if (enableForcedUpdate) {
            // Update battery status no more than once an hour
            if (!atomicState?.lastBatteryUpdate || (now - atomicState?.lastBatteryUpdate) > (batteryPollInterval.toInteger() * 60 * 60 * 1000)) {
                updateBattery = true
                atomicState?.lastBatteryUpdate = now
            }

            if (logEnable) log.debug "pollDevices: updateBattery = ${updateBattery}"

            getShadeDevices().eachWithIndex { device, index ->
                if (device != null) {
                    def shadeId = dniToShadeId(device.deviceNetworkId)
            
                    if (logEnable) log.debug "Running pollShadeDelayed() with runDelay = ${runDelay} for shade ${shadeId} (index = ${index})"
            
                    runIn(runDelay, "pollShadeDelayed", [overwrite: false, data: [shadeId: shadeId, updateBattery: updateBattery]])
                    runDelay += 5
                } else {
                    if (logEnable) log.debug "Got null shade device, index ${index}"
                }
            }
        } else {
            runIn(1, "pollShades", [overwrite: false, data: [updateBattery: updateBattery]])
        }
    }

    if (enableRepeaterPoll || firstPoll) {
		getRepeaterDevices().eachWithIndex { device, index ->
			if (device != null) {
				def repeaterId = dniToRepeaterId(device.deviceNetworkId)
            
				if (logEnable) log.debug "Running pollRepeaterDelayed() with runDelay = ${runDelay} for repeater ${repeaterId}"
            
				runIn(runDelay, "pollRepeaterDelayed", [overwrite: false, data: [repeaterId: repeaterId]])
				runDelay += 5
			} else {
				if (logEnable) log.debug "Got null repeater device, index ${index}"
			}
		}
	}
}

def pollShadeDelayed(data) {
    if (logEnable) log.debug "pollShadeDelayed: data: ${data}"
    pollShadeId(data.shadeId, data.updateBattery)
}

def pollRepeaterDelayed(data) {
    if (logEnable) log.debug "pollRepeaterDelayed: data: ${data}"
    pollRepeaterId(data.repeaterId)
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
def getRepeaterDniPrefix() {
    return "PowerView-Repeater-"
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

def repeaterIdToDni(id) {
    return "${getRepeaterDniPrefix()}${id}"
}

def dniToRepeaterId(dni) {
    def prefix = getRepeaterDniPrefix()
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

def getRepeaterDevice(repeaterId) {
    return getChildDevice(repeaterIdToDni(repeaterId))
}

def getRepeaterDevices() {
    return atomicState?.repeaters?.keySet().collect {
        getChildDevice(repeaterIdToDni(it))
    }
}

// data can contain 'shades', 'scenes', "rooms' and/or 'repeaters' -- only deviceData for specified device types is updated
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
    if (data?.repeaters) {
        deviceData["repeaters"] = data?.repeaters
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

def getSelectedRepeaters(Collection selectedRepeaterIDs) {
    return getSelectedDevices(atomicState?.deviceData?.repeaters, selectedRepeaterIDs)
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

def getDiscoveredRepeaterList() {
    def ret = [:]
    atomicState?.deviceData?.repeaters.each { repeater ->
            ret[repeater.key] = repeater.value
    }
    return ret
}

/*
 * PowerView API
 */

// FIRMWARE
def getFirmwareVer() {
    callPowerView("fwversion", firmwareVerCallback)
    atomicState?.gettingFirmwareVer = true
}

void firmwareVerCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered firmwareVerCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    state.fwName = hubResponse.json.firmware.mainProcessor.name
    state.fwRevision = hubResponse.json.firmware.mainProcessor.revision
    state.fwSubRevision = hubResponse.json.firmware.mainProcessor.subRevision
    state.fwBuild = hubResponse.json.firmware.mainProcessor.build
    
    atomicState?.gettingFirmwareVer = false
}

// ROOMS

def getRooms() {
    if (logEnable) log.debug "Entered getRooms()..."
    callPowerView("rooms", roomsCallback)
}

def openRoom(roomDevice) {
    if (logEnable) log.debug "openRoom: roomDevice = ${roomDevice}"

    def roomId = dniToRoomId(roomDevice.deviceNetworkId)
    def sceneId = atomicState?.rooms[roomId]?.openScene
    if (sceneId) {
        triggerScene(sceneId)
    } else {
        log.info "no open scene configured for room ${roomId}"
    }
}

def closeRoom(roomDevice) {
    if (logEnable) log.debug "closeRoom: roomDevice = ${roomDevice}"

    def roomId = dniToRoomId(roomDevice.deviceNetworkId)
    def sceneId = atomicState?.rooms[roomId]?.closeScene
    if (sceneId) {
        triggerScene(sceneId)
    } else {
        log.info "no close scene configured for room ${roomId}"
    }
}

void roomsCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered roomsCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def rooms = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.roomData.each { room ->
            def name = new String(room.name.decodeBase64())
            rooms[room.id] = name
            if (logEnable) log.debug "room: ID = ${room.id}, name = ${name}"
        }
    } else {
        log.error "roomsCallback() HTTP Error: ${hubResponse.status}, Headers: ${hubResponse.headers}, Body: ${hubResponse.body}"
    }
    
    updateDeviceDataState([rooms: rooms])
    
    atomicState?.gettingRooms = false
    atomicState?.gettingShades = true
}

// SCENES

def getScenes() {
    if (logEnable) log.debug "Entered getScenes()..."
    callPowerView("scenes", scenesCallback)
}

def triggerSceneFromDevice(sceneDevice) {
    def sceneId = dniToSceneId(sceneDevice.deviceNetworkId)
    triggerScene(sceneId)
}

def triggerScene(sceneId) {
    callPowerView("scenes?sceneId=${sceneId}", triggerSceneCallback)
}

void scenesCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered scenesCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def scenes = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.sceneData.each {scene ->
            def name = new String(scene.name.decodeBase64())
            scenes[scene.id] = name
            if (logEnable) log.debug "scene: ID = ${scene.id}, name = ${name}"
        }
    } else {
        log.error "scenesCallback() HTTP Error: ${hubResponse.status}, Headers: ${hubResponse.headers}, Body: ${hubResponse.body}"
    }
    
    updateDeviceDataState([scenes: scenes])
    
    atomicState?.gettingScenes = false
    atomicState?.gettingRepeaters = true
}

def triggerSceneCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered triggerScenesCallback()..."

    if (hubResponse.status != 200) {
        log.warn("got unexpected response: status=${hubResponse.status} body=${hubResponse.body}")
    } else {
        runIn(15, pollDevices)
    }
}

// SHADES 

def getShades() {
    if (logEnable) log.debug "Entered getShades()..."
    callPowerView("shades", shadesCallback)
}

def pollShade(shadeDevice, updateBatteryStatus = false) {
    if (logEnable) log.debug "pollShade: shadeDevice = ${shadeDevice}"
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)
    pollShadeId(shadeId)
}

def pollShadeId(shadeId, updateBatteryStatus = false) {
    if (logEnable) log.debug "pollShadeId: shadeId = ${shadeId}"

    def query = [:]
    if (updateBatteryStatus)
        query = [updateBatteryLevel: "true"]
    else
        query = [refresh: "true"]

    callPowerView("shades/${shadeId}", shadePollCallback, query)
}

def pollShades(data) {
    if (logEnable) log.debug "pollShades: data = ${data}"
    
    def query = [:]
    if (data != null && data.updateBattery) {
        if (logEnable) log.debug "pollShades: forcing synchronization of battery level state"
        query = [updateBatteryLevel: "true"]
    }

    callPowerView("shades", shadesPollCallback, query)
}

def calibrateShade(shadeDevice) {
    if (logEnable) log.debug "calibrateShade: shadeDevice = ${shadeDevice}"
    moveShade(shadeDevice, [motion: "calibrate"])
}

def jogShade(shadeDevice) {
    if (logEnable) log.debug "jogShade: shadeDevice = ${shadeDevice}"
    moveShade(shadeDevice, [motion: "jog"])
}

def stopShade(shadeDevice) {
    if (logEnable) log.debug "stopShade: shadeDevice = ${shadeDevice}"
    moveShade(shadeDevice, [motion: "stop"])
}

def setPosition(shadeDevice, positions) {
    if (logEnable) log.debug "setPosition: shadeDevice = ${shadeDevice}, positions = ${positions}"

    def shadePositions = [:]
    def positionNumber = 1

    if (positions?.containsKey("bottomPosition")) {
        shadePositions["posKind${positionNumber}"] = 1
        shadePositions["position${positionNumber}"] = (int)(positions.bottomPosition * 65535 / 100)
        positionNumber += 1
    }

    if (positions?.containsKey("topPosition")) {
        shadePositions["posKind${positionNumber}"] = 2
        shadePositions["position${positionNumber}"] = (int)(positions.topPosition * 65535 / 100)
    }

    if (positions?.containsKey("position")) {
        shadePositions["posKind${positionNumber}"] = 1
        shadePositions["position${positionNumber}"] = (int)(positions.position * 65535 / 100)
    }

    if (positions?.containsKey("tiltPosition")) {
        def childDevice = getShadeDevice(dniToShadeId(shadeDevice.deviceNetworkId))
        def max = childDevice.supportsTilt180() ? 65535 : 32767
        shadePositions["posKind${positionNumber}"] = 3
        shadePositions["position${positionNumber}"] = (int)(positions.tiltPosition * max / 100)
    }
    
    moveShade(shadeDevice, [positions: shadePositions])
}

def moveShade(shadeDevice, movementInfo) {
    def shadeId = dniToShadeId(shadeDevice.deviceNetworkId)

    def body = [:]
    body["shade"] = movementInfo

    def json = new groovy.json.JsonBuilder(body)
    callPowerView("shades/${shadeId}", setPositionCallback, null, "PUT", json.toString())
}

void shadePollCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered shadePollCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def shade = hubResponse.json.shade
    def childDevice = getShadeDevice(shade.id)

    if (logEnable) log.debug "shadePollCallback for shade id ${shade.id}, calling device ${childDevice}"
    childDevice.handleEvent(shade)
}

def shadesPollCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "In shadesPollCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    if (hubResponse.status == 200) {
        hubResponse.json.shadeData.each { shade ->
            def childDevice = getShadeDevice(shade.id)
            
            if (childDevice != null)
                childDevice.handleEvent(shade)
        }
    } else {
        log.error "shadesPollCallback() HTTP Error: ${hubResponse.status}, Headers: ${hubResponse.headers}, Body: ${hubResponse.body}"
    }
}

void setPositionCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered setPositionCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def shade = hubResponse.json.shade
    def childDevice = getShadeDevice(shade.id)

    if (logEnable) log.debug "setPositionCallback for shadeId ${shade.id}, calling device ${childDevice}"
    childDevice.handleEvent(shade)
}


void shadesCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered shadesCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def shades = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.shadeData.each { shade ->
            def name = shade.name ? new String(shade.name.decodeBase64()) : "Shade ID ${shade.id}"
            shades[shade.id] = name
            if (logEnable) log.debug "shade: ID = ${shade.id}, name = ${name}"
        }
    } else {
        log.error "shadesCallback() HTTP Error: ${hubResponse.status}, Headers: ${hubResponse.headers}, Body: ${hubResponse.body}"
    }
    
    updateDeviceDataState([shades: shades])
    
    atomicState?.gettingShades = false
    atomicState?.gettingScenes = true
}

// REPEATERS

def getRepeaters() {
    if (logEnable) log.debug "Entered getRepeaters()..."
    callPowerView("repeaters", repeatersCallback)
}

void repeatersCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered repeatersCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def repeaters = [:]
    
    if (hubResponse.status == 200) {
        hubResponse.json.repeaterData.each { repeater ->
            def name = repeater.name ? new String(repeater.name.decodeBase64()) : "Repeater ID ${repeater.id}"
            repeaters[repeater.id] = name
            if (logEnable) log.debug "repeater: ID = ${repeater.id}, name = ${name}"
        }
    } else {
        log.error "repeatersCallback() HTTP Error: ${hubResponse.status}, Headers: ${hubResponse.headers}, Body: ${hubResponse.body}"
    }
    
    updateDeviceDataState([repeaters: repeaters])
    
    atomicState?.gettingRepeaters = false
}

def pollRepeater(repeaterDevice) {
    if (logEnable) log.debug "pollRepeater: repeaterDevice = ${repeaterDevice}"

    def repeaterId = dniToRepeaterId(repeaterDevice.deviceNetworkId)
    pollRepeaterId(repeaterId)
}

def pollRepeaterId(repeaterId) {
    if (logEnable) log.debug "pollRepeaterId: repeaterId = ${repeaterId}"

    callPowerView("repeaters/${repeaterId}", repeaterPollCallback)
}

def setRepeaterPrefs(repeaterDevice, prefs) {
    def repeaterId = dniToRepeaterId(repeaterDevice.deviceNetworkId)

    def body = [:]
    body["repeater"] = prefs

    def json = new groovy.json.JsonBuilder(body)
    callPowerView("repeaters/${repeaterId}", setRepeaterPrefsCallback, null, "PUT", json.toString())
}

void setRepeaterPrefsCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered setRepeaterPrefsCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def repeater = hubResponse.json.repeater
    def childDevice = getRepeaterDevice(repeater.id)

    if (logEnable) log.debug "setRepeaterPrefs Callback for repeaterId ${repeater.id}, calling device ${childDevice}"
    childDevice.handleEvent(repeater)
}

void repeaterPollCallback(hubitat.device.HubResponse hubResponse) {
    if (logEnable) log.debug "Entered repeaterPollCallback()..."
    if (logEnable) log.debug "json: ${hubResponse.json}"

    def repeater = hubResponse.json.repeater
    def childDevice = getRepeaterDevice(repeater.id)

    if (logEnable) log.debug "repeaterPollCallback for repeater id ${repeater.id}, calling device ${childDevice}"
    childDevice.handleEvent(repeater)
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
    def fullPath = "/api/${path}"

    if (logEnable) log.debug "callPowerView: url = 'http://${host}${fullPath}', method = '${method}', body = '${body}', query = ${query}, callback = ${callback}"

    def headers = [
        "HOST": host,
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

// UTILITY FUNCTIONS

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
