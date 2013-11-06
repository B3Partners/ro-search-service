package nl.b3p.ro.search;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.Query;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.util.logging.Logging;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsEqualTo;

/**
 *
 * @author Roy Braam
 */
public class Zoeker {
    private static final Log log = LogFactory.getLog(Zoeker.class);
    private static final String roOnlineUrl= "http://afnemers.ruimtelijkeplannen.nl/afnemers/services?REQUEST=GetCapabilities&Service=WFS";
    private static Map conProps = new HashMap();
    private static String featureType= "app:Plangebied";
    private static final FilterFactory2 ff;
    static{
        conProps.put("WFSDataStoreFactory:GET_CAPABILITIES_URL",roOnlineUrl);
        ff = CommonFactoryFinder.getFilterFactory2();
    }
    
    public Zoeker (){        
    }
    
    public static JSONObject zoekPlan(String gemeenteCode) throws JSONException{
        DataStore ds =null;
        JSONArray array = new JSONArray();
        String error = null;
        //get RO-Online features ophalen
        try{
            
            ds = DataStoreFinder.getDataStore(conProps);
            FeatureSource fs = ds.getFeatureSource(featureType);
            PropertyIsEqualTo filter = ff.equals(ff.property("overheidscode"), ff.literal(gemeenteCode));
            Query q = new Query(featureType,filter, new String[]{
                "overheidscode",
                "geometrie",
                "naam",
                "identificatie",
                "verwijzingNaarTekst",
                "typePlan",
                "planstatus",
            });
            
            FeatureCollection fc = fs.getFeatures(q);
            FeatureIterator it = fc.features();
            try{
                while(it.hasNext()){
                    Feature f =it.next();
                    JSONObject jsonFeature = createJSONFeature(f);
                    array.put(jsonFeature);
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
        //tercera search
        
        
        
        JSONObject resultObj = new JSONObject();
        if (error!=null){
            resultObj.put("error",error);            
        }
        resultObj.put("results",array);
        return resultObj;
    }

    
    private static JSONObject createJSONFeature(Feature f) throws JSONException {
        JSONObject json = new JSONObject();
        if (f!=null){
            Collection<Property> properties= f.getProperties();
            Iterator<Property> it =properties.iterator();
            while (it.hasNext()){
                Property property = it.next();
                json.put(property.getName().getLocalPart(),property.getValue());
            }
        }
        return json;
    }
    
    
    public static void main(String[] args) throws JSONException, ClassNotFoundException{
        Logging.ALL.setLoggerFactory("org.geotools.util.logging.Log4JLoggerFactory");
        JSONObject obj = Zoeker.zoekPlan("0355");
        if (obj.has("error")){
            System.out.println("error: "+obj.getJSONArray("error"));
        }if (obj.has("results")){
            System.out.println("results: "+obj.getJSONArray("results").length());
        }
    }
}
