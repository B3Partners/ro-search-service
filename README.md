ro-search-service
=================

Ruimtelijke plannen zoek service.

=================
Add TerceraSOAPUsername and TerceraSOAPPassword for tercera service in context.xml at:
CATALINA_BASE/conf/
For example:

<Context>
  <Parameter name="TerceraSOAPUsername" override="false" value="Username"/>
  <Parameter name="TerceraSOAPPassword" override="false" value="Password"/>
</Context>
