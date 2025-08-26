def latest = new File('latest').canonicalFile
new File('.').eachFile {
    def fr = new File(it, 'fr/jsolex.html')
    if (it.canonicalFile != latest && fr.exists() && !it.toString().contains('LATEST')) {
        def txt = fr.text
        if (!txt.contains('ancienne version')) {
            txt = txt.replace('''<div id="preamble">
<div class="sectionbody">''', '''<div id="preamble">
<div class="sectionbody"><div class="admonitionblock warning">
<table>
<tr>
<td class="icon">
<i class="fa icon-warning" title="Warning"></i>
</td>
<td class="content">
<div class="paragraph">
<p>Vous regardez la documentation d&#8217;une ancienne version.
Téléchargez la dernière version <a href="https://melix.github.io/astro4j/latest/fr/jsolex.html" class="bare">ici</a></p>
</div>
</td>
</tr>
</table>''')
            fr.text = txt
        }
        def en = new File(it, 'en/jsolex.html')
        if (it.canonicalFile != latest && en.exists() && !en.toString().contains('LATEST')) {
            txt = en.text
            if (!txt.contains('old documentation version')) {
                txt = txt.replace('''<div id="preamble">
<div class="sectionbody">''', '''<div id="preamble">
<div class="sectionbody"><div class="admonitionblock warning">
<table>
<tr>
<td class="icon">
<i class="fa icon-warning" title="Warning"></i>
</td>
<td class="content">
<div class="paragraph">
<p>You are looking at an old documentation version.
You can download the latest release by following <a href="https://melix.github.io/astro4j/latest/en/jsolex.html" class="bare">this link</a></p>
</div>
</td>
</tr>
</table>''')
                en.text = txt
            }
        }
    }
}

new File('.')
        .listFiles()
        .findAll { it.name.endsWith('-SNAPSHOT') }
        .sort { File it -> it.lastModified() }[0..<-1]
        .each {
            "rm -rf ${it}".execute()
        }
