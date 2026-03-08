/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.app.jfx

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.Subject

@Subject(SpectroSolHubApi)
class SpectroSolHubApiTest extends Specification {

    static final String LIST_RESPONSE = '''{
        "repositories": [
            {
                "name": "Cédric's scripts",
                "slug": "cedrics-scripts",
                "description": "A repository containing scripts I regularly use.",
                "owner": "melix",
                "scriptCount": 2,
                "starCount": 0
            }
        ]
    }'''

    static final String DETAIL_RESPONSE = '''{
        "description": "A repository containing scripts I regularly use.",
        "name": "Cédric's scripts",
        "owner": "melix",
        "scripts": [
            {
                "author": "Cédric Champeau",
                "description": "Used in batch mode, this script will automatically stack images and generate: a stacked image with classic processing, an enhanced image, a negative image, prominences, Doppler and Doppler eclipse, as well as continuum and active regions",
                "downloadCount": 1,
                "fileCount": 1,
                "filename": "stack-Ha-aggressive-unsharp.math",
                "requiresVersion": "4.4.3",
                "title": "Aggressive H-alpha Stacking with Unsharp Mask",
                "version": "1.3"
            },
            {
                "author": "Cédric Champeau",
                "description": "Creates a false-color tomography image revealing chromospheric structures at different altitudes by combining 5 wavelength layers. Green highlights the highest features (prominences, spicules at the limb), magenta/pink shows mid-altitude chromospheric activity on the disk, while blue and red tints reveal Doppler-shifted plasma (material moving toward or away from us).",
                "downloadCount": 1,
                "fileCount": 1,
                "filename": "tomography.math",
                "requiresVersion": "4.3.0",
                "title": "Solar Tomography",
                "version": "1.0"
            }
        ],
        "slug": "cedrics-scripts",
        "starCount": 0
    }'''

    static final String MULTI_REPO_LIST_RESPONSE = '''{
        "repositories": [
            {
                "name": "Wow",
                "slug": "wow",
                "description": "My awesome scripts",
                "owner": "admin",
                "scriptCount": 1,
                "starCount": 3
            },
            {
                "name": "Cédric's scripts",
                "slug": "cedrics-scripts",
                "description": "A repository containing scripts I regularly use.",
                "owner": "melix",
                "scriptCount": 2,
                "starCount": 0
            }
        ]
    }'''

    void "parses repository list with one repository"() {
        when:
        var repos = SpectroSolHubApi.parseRepositoryList(LIST_RESPONSE)

        then:
        repos.size() == 1
        repos.getFirst().name() == "Cédric's scripts"
        repos.getFirst().slug() == "cedrics-scripts"
        repos.getFirst().description() == "A repository containing scripts I regularly use."
        repos.getFirst().owner() == "melix"
        repos.getFirst().scriptCount() == 2
        repos.getFirst().starCount() == 0
    }

    void "parses repository list with multiple repositories"() {
        when:
        var repos = SpectroSolHubApi.parseRepositoryList(MULTI_REPO_LIST_RESPONSE)

        then:
        repos.size() == 2
        repos[0].name() == "Wow"
        repos[0].owner() == "admin"
        repos[0].scriptCount() == 1
        repos[0].starCount() == 3
        repos[1].name() == "Cédric's scripts"
        repos[1].owner() == "melix"
    }

    void "parses empty repository list"() {
        when:
        var repos = SpectroSolHubApi.parseRepositoryList('{"repositories": []}')

        then:
        repos.isEmpty()
    }

    void "parses repository detail with all scripts"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail(DETAIL_RESPONSE)

        then:
        detail.name() == "Cédric's scripts"
        detail.slug() == "cedrics-scripts"
        detail.description() == "A repository containing scripts I regularly use."
        detail.owner() == "melix"
        detail.starCount() == 0
        detail.scripts().size() == 2
    }

    void "parses first script details correctly"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail(DETAIL_RESPONSE)
        var script = detail.scripts().getFirst()

        then:
        script.filename() == "stack-Ha-aggressive-unsharp.math"
        script.title() == "Aggressive H-alpha Stacking with Unsharp Mask"
        script.author() == "Cédric Champeau"
        script.version() == "1.3"
        script.description().startsWith("Used in batch mode")
        script.requiresVersion() == "4.4.3"
        script.fileCount() == 1
        script.downloadCount() == 1
    }

    void "parses second script details correctly"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail(DETAIL_RESPONSE)
        var script = detail.scripts().get(1)

        then:
        script.filename() == "tomography.math"
        script.title() == "Solar Tomography"
        script.author() == "Cédric Champeau"
        script.version() == "1.0"
        script.description().startsWith("Creates a false-color tomography")
        script.requiresVersion() == "4.3.0"
        script.fileCount() == 1
        script.downloadCount() == 1
    }

    void "parses detail with no scripts array"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail('''{
            "name": "Empty",
            "slug": "empty",
            "description": "",
            "owner": "someone",
            "starCount": 0
        }''')

        then:
        detail.name() == "Empty"
        detail.scripts().isEmpty()
    }

    void "parses detail with null scripts array"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail('''{
            "name": "NullScripts",
            "slug": "null-scripts",
            "description": "test",
            "owner": "someone",
            "scripts": null,
            "starCount": 0
        }''')

        then:
        detail.name() == "NullScripts"
        detail.scripts().isEmpty()
    }

    void "getStringField returns empty for missing field"() {
        given:
        var obj = new JsonObject()

        expect:
        SpectroSolHubApi.getStringField(obj, "missing") == ""
    }

    void "getStringField returns empty for null field"() {
        given:
        var obj = new JsonObject()
        obj.add("nullField", JsonNull.INSTANCE)

        expect:
        SpectroSolHubApi.getStringField(obj, "nullField") == ""
    }

    void "getStringField returns value for present field"() {
        given:
        var obj = new JsonObject()
        obj.addProperty("name", "test")

        expect:
        SpectroSolHubApi.getStringField(obj, "name") == "test"
    }

    void "getIntField returns zero for missing field"() {
        given:
        var obj = new JsonObject()

        expect:
        SpectroSolHubApi.getIntField(obj, "missing") == 0
    }

    void "getIntField returns zero for null field"() {
        given:
        var obj = new JsonObject()
        obj.add("nullField", JsonNull.INSTANCE)

        expect:
        SpectroSolHubApi.getIntField(obj, "nullField") == 0
    }

    void "getIntField returns value for present field"() {
        given:
        var obj = new JsonObject()
        obj.addProperty("count", 42)

        expect:
        SpectroSolHubApi.getIntField(obj, "count") == 42
    }

    void "buildScriptsUrl constructs correct URL"() {
        given:
        var api = new SpectroSolHubApi("https://spectrosolhub.com")

        expect:
        api.buildScriptsUrl("melix", "cedrics-scripts") == "https://spectrosolhub.com/scripts/melix/cedrics-scripts/"
    }

    void "parses detail with empty description"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail('''{
            "name": "NoDesc",
            "slug": "no-desc",
            "description": "",
            "owner": "someone",
            "scripts": [
                {
                    "filename": "test.math",
                    "title": "Test Script",
                    "author": "Author",
                    "version": "1.0",
                    "description": "",
                    "requiresVersion": "",
                    "fileCount": 1,
                    "downloadCount": 0
                }
            ],
            "starCount": 5
        }''')

        then:
        detail.description() == ""
        detail.starCount() == 5
        detail.scripts().size() == 1
        detail.scripts().getFirst().title() == "Test Script"
        detail.scripts().getFirst().description() == ""
        detail.scripts().getFirst().downloadCount() == 0
    }

    void "totalDownloads sums script download counts"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail(DETAIL_RESPONSE)

        then:
        detail.totalDownloads() == 2
    }

    void "totalDownloads is zero for empty scripts"() {
        when:
        var detail = SpectroSolHubApi.parseRepositoryDetail('''{
            "name": "Empty",
            "slug": "empty",
            "description": "",
            "owner": "someone",
            "starCount": 0
        }''')

        then:
        detail.totalDownloads() == 0
    }
}
