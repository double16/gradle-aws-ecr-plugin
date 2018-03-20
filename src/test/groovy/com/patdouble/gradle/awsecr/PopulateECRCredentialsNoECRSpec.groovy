package com.patdouble.gradle.awsecr

import com.amazonaws.services.ecr.AmazonECRClient
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Test PopulateECRCredentials when no ECR registries are configured.
 */
class PopulateECRCredentialsNoECRSpec extends Specification {
    PopulateECRCredentials task
    AmazonECRClient amazonECRClient

    @Rule TemporaryFolder tmp

    def setup() {
        amazonECRClient = GroovySpy(AmazonECRClient, global: true)

        def project = ProjectBuilder.builder().withProjectDir(tmp.newFolder('project')).build()
        project.with {
            apply plugin: 'com.patdouble.awsecr'
        }
        project.evaluate()
        task = project.tasks.getByName('populateECRCredentials')
        task.credFileDirectory = tmp.newFolder('ecr')
    }
    
    void "task should be disabled"() {
        expect:
        task.enabled
        !task.getOnlyIf().isSatisfiedBy(task)
    }
}
