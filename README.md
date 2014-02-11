ro-search-service
=================

Ruimtelijke plannen zoek service.

=================
Add TerceraSOAPUsername and TerceraSOAPPassword for tercera service in context.xml at:
CATALINA_BASE/conf/
For example:
```xml
<Context>
  <Parameter name="TerceraSOAPUsername" override="false" value="Username"/>
  <Parameter name="TerceraSOAPPassword" override="false" value="Password"/>
</Context>
```

Build:
First build the tercera-soap-client so it's in your mvn repo:
https://github.com/B3Partners/tercera-soap-client
