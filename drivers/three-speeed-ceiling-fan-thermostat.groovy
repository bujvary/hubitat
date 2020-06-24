/*
  Virtual Thermostat for 3 Speed Ceiling Fan Control (PARENT)
  Copyright 2016 SmartThings, Dale Coffing

  Modified by Brian Ujvary

  Change Log
  2020-06-24 v1.0.0 bujvary - Changed path to icons
                              Added ImportURL
                              Renamed app

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
  in compliance with the License. You may obtain a copy of the License at: www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
  for the specific language governing permissions and limitations under the License.
*/

definition(
    name: "Three Speed Ceiling Fan Thermostat",
    namespace: "dcoffing",
    author: "Dale Coffing",
    description: "Automatic control for 3 Speed Ceiling Fan using Low, Medium, High speeds with any temperature sensor.",
    category: "Convenience",
    singleInstance: true,
	iconUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/3scft125x125.png", 
   	iconX2Url: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/3scft250x250.png",
	iconX3Url: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/3scft250x250.png",
    importURL: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/three-speeed-ceiling-fan-thermostat.groovy"
)

preferences {
        page(name: "parentPage")
        page(name: "aboutPage")
}


def parentPage() {
	return dynamicPage(name: "parentPage", title: "", nextPage: "", install: true, uninstall: true) {
        section("Create a new fan automation.") {
            app(name: "childApps", appName: "Ceiling Fan Thermostat" , namespace: "dcoffing", title: "New Fan Automation", multiple: true)
        }
    }
}

