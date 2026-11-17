{# Pebble removes the newline that follows a tag, so a line ending in a tag needs one extra newline #}
{% for class in classes %}
{% if class.description is not null %}
{{ class.description | raw }}


{% endif %}
{% for method in class.methods %}
#### {{ method.name }} <sup>[source]({{ method.link }})</sup>

{% if method.description is not null %}
{{ method.description | raw }}


{% endif %}
```java
{{ method.body | raw }}

```

{% endfor %}
{% endfor %}
