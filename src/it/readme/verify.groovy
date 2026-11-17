def readme = new File(basedir, 'target/docs/README.md').text
assert readme.contains('#### createList <sup>[source](src/test/java/com/example/docs/GettingStarted.java#L10-L22)</sup>')
assert readme.contains('Getting started with the example library.')
assert readme.contains('Create a list and check its size.')
assert readme.contains('''```java
final List<String> list = List.of("a", "b");

// Lists know their size
if (list.size() != 2)
{
    throw new AssertionError();
}
```''')
assert !readme.contains('helper')

def html = new File(basedir, 'target/source-extractor/samples-html.html').text
assert html.contains('<pre><code class="language-java">final List&lt;String&gt; list')

assert new File(basedir, 'target/source-extractor/src-test-java-com-example-docs.md').isFile()
