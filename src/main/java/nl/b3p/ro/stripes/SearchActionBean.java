/*
 * Copyright (C) 2012-2013 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.ro.stripes;

import com.microsoft.schemas.sql.sqlrowset1.SqlRowSet1.Row;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.ro.tercera.soap.SoapClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.data.ows.Layer;
import org.geotools.data.wms.WMSUtils;
import org.geotools.data.wms.WebMapServer;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.ows.ServiceException;
import org.geotools.util.logging.Logging;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.geometry.BoundingBox;

/**
 *
 * @author Roy Braam
 */

@UrlBinding("/action/search/{$event}")
@StrictBinding
public class SearchActionBean implements ActionBean{
    private static final Log log = LogFactory.getLog(SearchActionBean.class);
    private static final String roOnlineUrl= "http://afnemers.ruimtelijkeplannen.nl/afnemers/services?REQUEST=GetCapabilities&Service=WFS";
    private static Map conProps = new HashMap();
    private static String featureType= "app:Plangebied";
    private static final FilterFactory2 ff;
    private static final String TERCERA = "Tercera";
    private static final String ROONLINE = "Roonline";
    @Validate
    private String overheidsCode=null;
    @Validate
    private Integer maxRestuls=1000;
    @Validate
    private String wmsUrl=null;
    private ActionBeanContext context;
    
    static{
        conProps.put("WFSDataStoreFactory:GET_CAPABILITIES_URL",roOnlineUrl);
        ff = CommonFactoryFinder.getFilterFactory2();
    }
    @DefaultHandler
    public Resolution zoekPlannen() throws JSONException{
        JSONObject resultObj = new JSONObject();
        JSONObject list = new JSONObject();
        resultObj.put("success",false);
        List<String> errors = new ArrayList<String>();
        
        if (this.getOverheidsCode()==null){
            errors.add("Geen overheidscode opgegeven.");
        }else{
        
            //ro Online
            String error=getRoOnlineFeatures(list);
            if (error!=null){
                errors.add(error);
            }

            //tercera search
            String terceraError = getTerceraFeatures(list);
            if (terceraError!=null){
                errors.add(terceraError);
            }
        }
              
        if (!errors.isEmpty()){
            String error = null;
            for (String e : errors){
                if (e!=null){
                    if (error==null){
                        error=e;
                    }else{
                        error+=" "+e;
                    }
                }
            }
            if (error!=null){
                resultObj.put("error", error);
            }
        }
        resultObj.put("results",list);
        resultObj.put("success",true);
        return new StreamingResolution("application/json",new StringReader(resultObj.toString()));        
    }
    
    public Resolution getTerceraWMSLayers(){
        JSONObject result = new JSONObject();
        JSONArray layers = new JSONArray();
        String error=null;
        Boolean success=false;
        try{
            if (wmsUrl!=null){
                List layerNames= getLayerNames(wmsUrl);
                Iterator<String> it=layerNames.iterator();
                while(it.hasNext()){
                    String s=it.next();
                    /*Remove the layer if there are more then one  "-" in the name
                      or if NLIMRO is in the name (top layer)
                     */
                    if (s.indexOf("NLIMRO") < 0 && (s.indexOf("-") < 0 || s.indexOf("-")==s.lastIndexOf("-"))){
                        layers.put(s);
                    }
                }
                success=true;
            }else{
                error = "No wms service url given.";
            }
            
            result.put("layers",layers);
        }catch(JSONException jse){
            success=false;
            log.error("Error while creating list of Layers for tercera service", jse);
        }
        try{
            if (error!=null){
                    result.put("error", error);
            }        
            result.put("success",success);
        }catch(JSONException jse){
            log.error("Error while setting error and/or success in JSON result");
        }
        return new StreamingResolution("application/json",new StringReader(result.toString()));  
    }
    /**
     * Gets the RO-Online features and adds them to the array.
     * @param array 
     * @return a error message. If null, no error occurred.
     */
    private String getRoOnlineFeatures(JSONObject list) {        
        DataStore ds =null;
        String error=null;
        
        //get RO-Online features ophalen
        try{
            
            ds = DataStoreFinder.getDataStore(conProps);
            FeatureSource fs = ds.getFeatureSource(featureType);
            PropertyIsEqualTo filter = ff.equals(ff.property("overheidscode"), ff.literal(this.getOverheidsCode()));
            Query q = new Query(featureType,filter, new String[]{
                "overheidscode",
                "geometrie",
                "naam",
                "identificatie",
                "verwijzingNaarTekst",
                "typePlan",
                "planstatus",
            });
            q.setMaxFeatures(this.maxRestuls);
            FeatureCollection fc = fs.getFeatures(q);
            FeatureIterator it = fc.features();
            try{
                while(it.hasNext()){
                    Feature f =it.next();
                    String id = (String) f.getProperty("identificatie").getValue();
                    if (!list.has(id)){
                        JSONObject jsonFeature = createJSONFeature(f);
                        list.put(id,jsonFeature);
                        if (list.length() >= this.maxRestuls){
                            break;
                        }
                    }
                }
            }finally{
                it.close();                
            }
            
            
        }catch(Exception e){
            e.printStackTrace();
            log.error("Error while loading Ro-online features",e);
            error = e.getLocalizedMessage();
        }
        finally{
            if (ds!=null){
                ds.dispose();
            }
        }
        return error;
    }
    
    private String getTerceraFeatures(JSONObject list){
        String sOAPUsername = this.getContext().getServletContext().getInitParameter("TerceraSOAPUsername");
        String sOAPPassword = this.getContext().getServletContext().getInitParameter("TerceraSOAPPassword");
        
        String error="";
        if (sOAPUsername==null || sOAPPassword ==null){
            error="Unable to load Tercera service. Cause:'TerceraSOAPUsername' and/or 'TerceraSOAPPassword' "
                    + "not configured in the Tomcat server. Add both param's to a context in the CATALINA_BASE/conf dir."
                    + "See: https://github.com/B3Partners/ro-search-service";
        }
        try{
            SoapClient client = new SoapClient(sOAPUsername,sOAPPassword);
            List<Row> rows = client.getPlannen();   
            //is logged in? Get private plans.
            if (context.getRequest().getUserPrincipal()!=null){
                String username=context.getRequest().getUserPrincipal().getName();
                List<Row> privateRows = client.getUserPlannen(username);
                if (privateRows!=null){
                    rows.addAll(privateRows);
                }
            }
            if (rows != null) {
                for (Row row : rows) {
                    if (this.getOverheidsCode().equals(row.getOverheidscode())){                    
                        try{
                            String id = row.getIdentificatie();
                            if(!list.has(id)){
                                JSONObject obj = createJSONFeature(row);                              
                                list.put(id,obj);
                            }
                        }catch(JSONException je){
                            error+=je.getLocalizedMessage();
                        }                    
                        if (this.getMaxRestuls() != null && this.getMaxRestuls() > 0 && list.length() >= this.getMaxRestuls()) {
                            break;
                        }
                    }
                }
            }
        }catch(Exception e){
            error=e.getLocalizedMessage();
            log.error("Error while getting tercera plans",e);
        }        
        if (error.length()==0){
            error=null;
        }
        return error;
    }
    /**
     * Create a json feature from a GeoTools feature
     * @param f the geotools feature
     * @return a json object representing the feature
     * @throws JSONException 
     */    
    private static JSONObject createJSONFeature(Feature f) throws JSONException {
        JSONObject json = new JSONObject();
        if (f!=null){            
            json.put("origin",ROONLINE);
            Collection<Property> properties= f.getProperties();
            Iterator<Property> it =properties.iterator();
            while (it.hasNext()){
                Property property = it.next();
                if(!(property.getType() instanceof GeometryType)){                    
                    json.put(property.getName().getLocalPart(),property.getValue());
                }
            }
            BoundingBox bb=f.getBounds();
            if (bb!=null){
                JSONObject bbox = new JSONObject();
                bbox.put("minx", bb.getMinX() );
                bbox.put("miny", bb.getMinY());
                bbox.put("maxx", bb.getMaxX());
                bbox.put("maxy", bb.getMaxY());
                json.put("bbox",bbox);
            }
        }
        return json;
    }
    
    private static JSONObject createJSONFeature(Row r) throws JSONException {
        JSONObject json = new JSONObject();
        if (r !=null){
            json.put("origin",TERCERA);
            json.put("overheidscode",r.getOverheidscode());            
            json.put("naam",r.getNaam());
            json.put("identificatie",r.getIdentificatie());
            json.put("verwijzingNaarTekst",r.getVerwijzingnaartekst());
            json.put("typePlan",r.getTypePlan());
            json.put("planstatus",r.getPlanstatus());
            if (r.getWmsrequest()!=null){
                json.put("wms",r.getWmsrequest());
            }
            if (r.getBbox()!=null){
                JSONObject bbox = new JSONObject();
                String[] tokens = r.getBbox().split(" ");
                if (tokens.length==4){
                    try{
                        bbox.put("minx", Double.parseDouble(tokens[0]));
                        bbox.put("miny", Double.parseDouble(tokens[1]));
                        bbox.put("maxx", Double.parseDouble(tokens[2]));
                        bbox.put("maxy",Double.parseDouble(tokens[3]));
                        json.put("bbox",bbox);
                    }catch(NumberFormatException nfe){ 
                        log.error("Can't set bbox for tercera feature with identification: "
                                +r.getIdentificatie(),nfe);
                    }                                        
                }
            }
            
        }
        return json;
    }
    
    private Layer[] getLayers(String url) throws URISyntaxException, IOException, ServiceException{
        URI uri = new URI(url);   
        
        WebMapServer wms = new WebMapServer(uri.toURL(),30000);
        Layer[] layers=WMSUtils.getNamedLayers(wms.getCapabilities());        
        return layers;
    }
    
    private List<String> getLayerNames(String url){        
        ArrayList<String> result = new ArrayList<String>();
        try{
            //url= url.replace("\\", "%5C");
            Layer[] layers=getLayers(url);            
            for (int i=0; i < layers.length; i++){
                result.add(layers[i].getName());
            }
        }catch(Exception e){
            log.error("Error when loading layers: ",e);
        }
        return result;
    }
    
    public static void main(String[] args) throws JSONException, ClassNotFoundException{
        Logging.ALL.setLoggerFactory("org.geotools.util.logging.Log4JLoggerFactory");
        SearchActionBean sab = new SearchActionBean();
        sab.overheidsCode="0355";
        StreamingResolution sr = (StreamingResolution) sab.zoekPlannen();
        
        System.out.println("Result: "+sr.toString());
        
    }
    //<editor-fold defaultstate="collapsed" desc="Getters/Setters">
    
    public ActionBeanContext getContext() {
        return context;
    }
    
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getOverheidsCode() {
        return overheidsCode;
    }

    public void setOverheidsCode(String overheidsCode) {
        this.overheidsCode = overheidsCode;
    }

    public Integer getMaxRestuls() {
        return maxRestuls;
    }

    public void setMaxRestuls(Integer maxRestuls) {
        this.maxRestuls = maxRestuls;
    }

    public String getWmsUrl() {
        return wmsUrl;
    }

    public void setWmsUrl(String wmsUrl) {
        this.wmsUrl = wmsUrl;
    }
}
//</editor-fold>
