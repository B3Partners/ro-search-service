ro-search-service
=================

Ruimtelijke plannen zoek service.

=================
Add username and password for tercera service in context.xml at:
CATALINA_BASE/conf/
For example:
<Context>
  <Parameter name="TerceraUsername" override="false" value="Username"/>
  <Parameter name="TerceraPassword" override="false" value="password"/>
</Context>
