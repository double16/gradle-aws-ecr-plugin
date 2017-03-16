package com.patdouble.gradle.awsecr

import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection

import java.util.regex.Pattern

class AwsecrPlugin implements Plugin<Project> {
    public static final String POPULATE_ECR_CREDENTIALS_NAME = 'populateECRCredentials'

    private static final Pattern AWS_ECR_URL = ~/(?:https:\/\/)?([0-9A-Za-z]+)\.dkr\.ecr\.[a-zA-Z0-9-]+\.amazonaws\.com/

    Project project
    PopulateECRCredentials populateECRCredentials

    @Override
    void apply(Project project) {
        assert project
        this.project = project
        project.plugins.apply(DockerRemoteApiPlugin)

        populateECRCredentials = createPopulateECRCredentialsTask()
        //Make sure there is an object to share references
        project.extensions.getByType(DockerExtension).with{
            if (!registryCredentials) {
                registryCredentials = new DockerRegistryCredentials()
            }
        }

        project.afterEvaluate {
            populateECRCredentials.registryCredentials = project.extensions.getByType(DockerExtension).registryCredentials

            TaskCollection<RegistryCredentialsAware> regTasks = project.tasks.withType(RegistryCredentialsAware).matching {!(it in PopulateECRCredentials)}
            configureRegistryCredentials(regTasks)
            regTasks*.dependsOn populateECRCredentials
        }
    }

    protected Task createPopulateECRCredentialsTask() {
        project.task(POPULATE_ECR_CREDENTIALS_NAME, type: PopulateECRCredentials) {
            group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
            description = 'Retrieve and use ECR registryCredentials'
            awsAccessKeyId = project.hasProperty('awsAccessKeyId') ? project['awsAccessKeyId'] : null
            awsSecretAccessKey = project.hasProperty('awsSecretAccessKey') ? project['awsSecretAccessKey'] : null

            logger = project.logger
            credFileDirectory = project.rootProject.file(".gradle")
        }
    }

    protected String findRepository(RegistryCredentialsAware registryCredentialsAware) {
        if (registryCredentialsAware.hasProperty('repository')) {
            return registryCredentialsAware['repository'] as String
        } else if (registryCredentialsAware.hasProperty('imageName')) {
            return registryCredentialsAware['imageName'] as String
        } else if (registryCredentialsAware.hasProperty('tag')) {
            return registryCredentialsAware['tag'] as String
        }
        project.logger.info("Skipping docker credentials for ${registryCredentialsAware} because we do not know how to find a repository for this type (${registryCredentialsAware.class}")
        null
    }

    /**
     * Extract the ECR repository ID and URL
     * @return map containing 'id' and 'url' keys or empty if repository URL is unknown
     */
    protected Map<String, String> extractEcrInfo(CharSequence repository) {
        def m = AWS_ECR_URL.matcher(repository)
        if (!m) {
            return [:]
        }

        ['id' : m.group(1),
         'url': m.group(0)]
    }

    protected void configureRegistryCredentials(TaskCollection<RegistryCredentialsAware> registryCredentialsAwareCollection) {
        String repository = registryCredentialsAwareCollection.findResult{ findRepository(it) }
        if (!repository) {
            project.logger.info('No compatible registries extracted')
            return
        }

        def info = extractEcrInfo(repository)
        if (!info) {
            project.logger.info("Skipping because repository '${repository}' is not AWS ECR")
            return
        }

        String registryId = info.id
        String registryUrl = info.url
        project.logger.info("Found ECR registry account ID ${registryId} at ${registryUrl}")
        populateECRCredentials.with{
            it.repository = repository
            it.registryId = registryId
            it.registryUrl = registryUrl
        }
    }
}
