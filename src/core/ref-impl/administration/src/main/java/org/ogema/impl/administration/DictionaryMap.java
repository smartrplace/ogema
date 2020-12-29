package org.ogema.impl.administration;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author jlapp
 */
public class DictionaryMap extends AbstractMap<String, Object>{
    
    final Dictionary dict;
    
    public DictionaryMap(Dictionary d) {
        this.dict = d;
    }

    @Override
    public Object put(String key, Object value) {
        return dict.put(key, value);
    }
    
    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> s = (Set<Entry<String, Object>>) Collections.list(dict.keys()).stream()
                .map(k -> new AbstractMap.SimpleEntry<>(String.valueOf(k), dict.get(k)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Entry<String, Object>> w = new LinkedHashSet<Entry<String, Object>>(s) {
            @Override
            public Iterator<Entry<String, Object>> iterator() {
                Iterator<Entry<String, Object>> it = super.iterator();
                Iterator<Entry<String, Object>> decorated = new Iterator<Entry<String, Object>>() {
                    Entry<String, Object> current;
                    @Override
                    public Entry<String, Object> next() {
                        return current = it.next();
                    }

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public void remove() {
                        dict.remove(current.getKey());
                        it.remove();
                    }
                    
                };
                return decorated;
            }
        };
        return w;
    }
    
}
