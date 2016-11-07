package com.patdouble.gradle.awsecr

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AwsecrPluginSpec extends Specification {
    static final String FAKE_REPO = 'https://123456789.dkr.ecr.us-east-1.amazonaws.com'

    def "apply() should load the plugin"() {
        given:
        def project = ProjectBuilder.builder().build()
        when:
        project.with {
            apply plugin: 'com.patdouble.awsecr'
        }
        project.evaluate()
        then:
        project.plugins.hasPlugin(AwsecrPlugin)
    }

    def "findRepository() for DockerPullImage"() {
        given:
        def project = ProjectBuilder.builder().build()
        def plugin = new AwsecrPlugin()
        def task = project.tasks.create('pull', DockerPullImage) {
            repository = FAKE_REPO
        }
        expect:
        plugin.findRepository(task) == FAKE_REPO
    }

    def "findRepository() for DockerBuildImage"() {
        given:
        def fakeTag = FAKE_REPO+'/myimage:latest'
        def project = ProjectBuilder.builder().build()
        def plugin = new AwsecrPlugin()
        def task = project.tasks.create('build', DockerBuildImage) {
            tag = fakeTag
        }
        expect:
        plugin.findRepository(task) == fakeTag
    }

    def "findRepository() for DockerPushImage"() {
        given:
        def fakeImage = FAKE_REPO+'/myimage'
        def project = ProjectBuilder.builder().build()
        def plugin = new AwsecrPlugin()
        def task = project.tasks.create('push', DockerPushImage) {
            imageName = fakeImage
        }
        expect:
        plugin.findRepository(task) == fakeImage
    }

    def "extractEcrInfo"() {
        given:
        def plugin = new AwsecrPlugin()
        def info = ['id':'123456789', 'url': FAKE_REPO]
        expect:
        plugin.extractEcrInfo(FAKE_REPO) == info
        plugin.extractEcrInfo(FAKE_REPO+'/myimage') == info
        plugin.extractEcrInfo(FAKE_REPO+'/myimage:latest') == info
        plugin.extractEcrInfo('').isEmpty()
        plugin.extractEcrInfo('library/mongo').isEmpty()
    }

    def "registry aware tasks are found"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.with {
            apply plugin: 'com.patdouble.awsecr'
            tasks.create('pull', DockerPullImage) {
                repository = FAKE_REPO
            }
            tasks.create('build', DockerBuildImage) {
                tag = FAKE_REPO+'/myimage:latest'
            }
            tasks.create('push', DockerPushImage) {
                imageName = FAKE_REPO+'/myimage'
            }
        }

        when:
        project.evaluate()

        then:
        thrown(GradleException)
    }
}