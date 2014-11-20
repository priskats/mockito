package org.mockito.release.notes.internal

import com.jcabi.github.Coordinates
import com.jcabi.github.Issue
import com.jcabi.github.Label
import com.jcabi.github.RtGithub;
import org.gradle.api.Project
import org.mockito.release.notes.PreviousVersionFromFile
import org.mockito.release.notes.ReleaseNotesBuilder
import org.mockito.release.notes.exec.Exec
import org.mockito.release.notes.vcs.Commit
import org.mockito.release.notes.vcs.ContributionSet
import org.mockito.release.notes.vcs.Vcs

class DefaultReleaseNotesBuilder implements ReleaseNotesBuilder {

    private final Project project
    private final String gitHubToken
    private final String ignorePattern
    private final ImprovementsPrinter improvementsPrinter

    DefaultReleaseNotesBuilder(Project project, String gitHubToken, String ignorePattern,
                               ImprovementsPrinter improvementsPrinter) {
        this.ignorePattern = ignorePattern
        this.gitHubToken = gitHubToken
        this.project = project
        this.improvementsPrinter = improvementsPrinter
    }

    void updateNotes(File notesFile, String toVersion) {
        println "Updating release notes file: $notesFile"
        def currentContent = notesFile.text
        def previousVersion = "v" + new PreviousVersionFromFile(notesFile).getPreviousVersion() //TODO SF duplicated, reuse service
        println "Building notes since $previousVersion until $toVersion"
        def newContent = buildNotesBetween(previousVersion, toVersion)
        notesFile.text = newContent + currentContent
        println "Successfully updated the release notes!"
    }

    ContributionSet getContributionsBetween(String fromRevision, String toRevision) {
        return Vcs.getContributionsProvider(Exec.getGradleProcessRunner(project)).getContributionsBetween(fromRevision, toRevision);
    }

    String buildNotesBetween(String fromVersion, String toVersion) {
        def tickets = new HashSet()
        ContributionSet contributions = getContributionsBetween(fromVersion, toVersion)
        println "Parsing ${contributions.allCommits.size()} commits"
        contributions.allCommits.each { Commit it ->
            def t = it.message.findAll("#\\d+")
            if (t) {
                tickets.addAll(t*.substring(1)) //get rid of leading '#'
            }

//this is helpful to find out what Google code issues we worked on:
//        def issues = it.findAll("[Ii]ssue \\d+")
//        if (issues) {
//            println "$issues found in $it"
//        }
        }
        ImprovementSet improvements = getImprovements(tickets)
        def date = new Date().format("yyyy-MM-dd HH:mm z", TimeZone.getTimeZone("UTC"))
        return """### $project.version ($date)

${contributions.toText()}
$improvements

"""
    }

    ImprovementSet getImprovements(Set<String> tickets) {
        if (tickets.empty) {
            return new OneCategoryImprovementSet(improvements: [])
        }
        //TODO we should query for all tickets via one REST call and stop using jcapi
        println "Querying GitHub API for ${tickets.size()} tickets. This may take a while."
        def github = new RtGithub(gitHubToken)
        def repo = github.repos().get(new Coordinates.Simple("mockito/mockito"))
        def issues = repo.issues()
        def out = []

        tickets.each {
            println " #$it"
            def i = issues.get(it as int)
            def issue = new Issue.Smart(i)
            if (issue.exists() && !issue.isOpen()) {
                out << new Improvement(id: issue.number(), title: issue.title(), url: issue.htmlUrl(),
                        labels: issue.labels().iterate().collect{ Label label -> label.name() })
            }
        }
//        new OneCategoryImprovementSet(improvements: out, ignorePattern: ignorePattern)
        new LabelledImprovementSet(out, ignorePattern, improvementsPrinter)
    }
}