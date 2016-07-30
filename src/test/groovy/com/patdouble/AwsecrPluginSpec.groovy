package com.patdouble

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AwsecrPluginSpec extends Specification {

    def "apply() should load the plugin"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.with {
            apply plugin: 'com.patdouble.awsecr'
        }

        then:
        project.plugins.hasPlugin(AwsecrPlugin)
    }

}
