/**
 *  Ring Virtual Chime Device Driver
 *
 *  Copyright 2019-2020 Ben Rimmasch
 *  Copyright 2021 Caleb Morse
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
 *    11/04/2023 Brian Ujvary v?.? - Added snooze() and cancelSnooze() commands (i.e. do_not_disturb)
 */

// @todo Consider using /ringtones

import groovy.transform.Field

metadata {
  definition(name: "Ring Virtual Chime", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch") {
    capability "Actuator"
    capability "AudioNotification"
    capability "AudioVolume"
    capability "Refresh"
    capability "Polling"
    capability "Tone"

    attribute "rssi", "number"
    attribute "wifi", "string"

    command "playDing"
    command "playMotion"
    command "snooze", [[name: "Set Snooze Length (in hours)*", type: "NUMBER", range: "1..24", description: "Choose a value between 1 and 24"]]
    command "cancelSnooze"
  }

  preferences {
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

void logInfo(msg) {
  if (descriptionTextEnable) { log.info msg }
}

void logDebug(msg) {
  if (logEnable) { log.debug msg }
}

void logTrace(msg) {
  if (traceLogEnable) { log.trace msg }
}

def parse(String description) {
  logDebug "description: ${description}"
}

def poll() {
  logDebug "poll()"
  refresh()
}

def refresh() {
  logDebug "refresh()"
  parent.apiRequestDeviceRefresh(device.deviceNetworkId)
  parent.apiRequestDeviceHealth(device.deviceNetworkId, "chimes")
}

void beep() { playMotion() }

void playMotion() {
  if (isMuted()) {
    logInfo "playMotion: Not playing because device is muted"
  } else {
    parent.apiRequestDeviceControl(device.deviceNetworkId, "chimes", "play_sound", [kind: "motion"])
  }
}

void playDing() {
  if (isMuted()) {
    logInfo "playDing: Not playing because device is muted"
  } else {
    parent.apiRequestDeviceControl(device.deviceNetworkId, "chimes", "play_sound", [kind: "ding"])
  }
}

void snooze(snoozetime) {
    // Value must be in [1, 24]
    snoozetime = Math.min(Math.max(snoozetime == null ? 1 : snoozetime.toInteger(), 1), 24)

    final Integer sentValue = snoozetime * 60
    
    parent.apiRequestDeviceControl(device.deviceNetworkId, "chimes", "do_not_disturb", [time: sentValue])
}

void cancelSnooze() {
    parent.apiRequestDeviceControl(device.deviceNetworkId, "chimes", "do_not_disturb", null)
}

void setVolume(volumelevel) {
  // Value must be in [0, 100]
  volumelevel = Math.min(Math.max(volumelevel == null ? 50 : volumelevel.toInteger(), 0), 100)

  Integer currentVolume = device.currentValue("volume")

  if (currentVolume != volumelevel) {
    logTrace "requesting volume change to ${volumelevel}"

    final Integer sentValue = volumelevel / 10

    parent.apiRequestDeviceSet(device.deviceNetworkId, "chimes", null, ["chime[settings][volume]": sentValue])
  }
  else {
    logInfo "Already at volume."
    sendEvent(name: "volume", value: currentVolume)
  }
}

void volumeUp() {
  Integer currentVolume = device.currentValue("volume")
  Integer nextVol = currentVolume + VOLUME_INC
  if (nextVol <= 100) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already max volume."
    sendEvent(name: "volume", value: currentVolume)
  }
}

void volumeDown() {
  Integer currentVolume = device.currentValue("volume")
  Integer nextVol = currentVolume - VOLUME_INC
  if (nextVol >= 0) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already min volume."
    sendEvent(name: "volume", value: currentVolume)
  }
}

void mute() {
  setVolume(0)
}

void unmute() {
  setVolume(state.prevVolume)
}

void updateVolumeInternal(volume) {
  Integer prevVolume = device.currentValue("volume")

  volume = volume.toInteger() * 10

  if (checkChanged("volume", volume)) {
    state.prevVolume == prevVolume
    if (volume == 0) {
      checkChanged("mute", "muted")
    } else {
      checkChanged("mute", "unmuted")
    }
  }
}

void playText(text, volumelevel) { log.error "playText not implemented!" }
void playTextAndRestore(text, volumelevel) { log.error "playTextAndRestore not implemented!" }
void playTextAndResume(text, volumelevel) { log.error "playTextAndResume not implemented!" }
def playTrack(trackuri, volumelevel) { log.error "playTrack not implemented!" }
def playTrackAndRestore(trackuri, volumelevel) { log.error "playTrackAndRestore not implemented!" }
def playTrackAndResume(trackuri, volumelevel) { log.error "playTrackAndResume not implemented!" }

private boolean isMuted() {
  return device.currentValue("mute") == "muted"
}

void handleDeviceControl(final String action, final Map msg, final Map query) {
  if (action == "play_sound") {
    if (query?.kind) {
      logInfo "Device ${device.label} played '${query?.kind}'"
    }
    else {
      log.error "handleDeviceControl unsupported play_sound with query ${query}"
    }
  }
  else if (action == "do_not_disturb") {
      if (query == null) {
        logInfo "Device ${device.label} snooze cancelled"
      }
      else {
        logInfo "Device ${device.label} snooze set to ${query?.time} minutes" 
      }
  }
  else {
    log.error "handleDeviceControl unsupported action ${action}"
  }
}

void handleDeviceSet(final String action, final Map msg, final Map query) {
  if (action == null) {
    if (query?.containsKey("chime[settings][volume]")) {
      updateVolumeInternal(query["chime[settings][volume]"])
    }
    else {
      log.error "handleDeviceSet unsupported null action with query ${query}"
    }
  }
  else {
    log.error "handleDeviceSet unsupported action ${action}, msg=${msg}, query=${query}"
  }
}

void handleHealth(final Map msg) {
  if (msg.device_health) {
    if (msg.device_health.wifi_name) {
      checkChanged("wifi", msg.device_health.wifi_name)
    }
  }
}

void handleRefresh(final Map msg) {
  if (msg.settings?.volume != null) {
    updateVolumeInternal(msg.settings.volume)
  }

  if (msg.kind != null) {
    checkChangedDataValue("kind", msg.kind)
  }
}

void runCleanup() {
  state.remove('lastUpdate')
  device.removeDataValue("firmware") // Doesn't appear to be available for this device
  device.removeDataValue("device_id")
}

boolean checkChanged(final String attribute, final newStatus, final String unit=null, final String type=null) {
  final boolean changed = device.currentValue(attribute) != newStatus
  if (changed) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
  }
  sendEvent(name: attribute, value: newStatus, unit: unit, type: type)
  return changed
}

void checkChangedDataValue(final String name, final value) {
  if (device.getDataValue(name) != value) {
    device.updateDataValue(name, value)
  }
}

@Field final static Integer VOLUME_INC = 10 // Chime volume is only in multiples of 10
