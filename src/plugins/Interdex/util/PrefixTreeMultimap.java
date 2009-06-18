/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.util;

import plugins.Interdex.util.PrefixTree.PrefixKey;

import com.google.common.collect.TreeMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
//import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Multiset;

import java.util.Map;
import java.util.Set;
import java.util.Collection;

/**
** Implementation of PrefixTree backed by a Map.
**
** TODO: make this implement SortedSetMultiMap
**
** @author infinity0
*/
public class PrefixTreeMultimap<K extends PrefixKey, V extends Comparable>
extends PrefixTree<K, V>
implements SetMultimap<K, V>/*, SortedSetMultimap<K,V>,
/*, Cloneable, Serializable*/ {

	/**
	** TreeMultimap holding the entries which don't need to be stored in their own
	** tree yet.
	*/
	final TreeMultimap<K, V> tmap;

	final PrefixTreeMultimap<K, V> parent;
	final PrefixTreeMultimap<K, V>[] child;

	protected PrefixTreeMultimap(K p, int len, int maxsz, TreeMultimap<K, V> tm, PrefixTreeMultimap<K, V>[] chd, PrefixTreeMultimap<K, V> par) {
		super(p, len, maxsz, chd, par);
		if (tm.size() + subtrees > maxsz) {
			throw new IllegalArgumentException("The TreeMultimap being attached is too big (> " + (maxsz-subtrees) + ")");
		}

		if (tm == null) {
			tmap = TreeMultimap.create();
		} else {
			tmap = tm;
		}
		//tmap = (tm == null)? TreeMultimap.create(): tm; // java sucks, so this doesn't work
		parent = (PrefixTreeMultimap<K, V>)super.parent;
		child = (PrefixTreeMultimap<K, V>[])super.child;
	}

	public PrefixTreeMultimap(K p, int len, int maxsz, PrefixTreeMultimap<K, V> par) {
		this(p, len, maxsz, null, (PrefixTreeMultimap<K, V>[])new PrefixTreeMultimap[p.symbols()], par);
	}

	public PrefixTreeMultimap(K p, int maxsz) {
		this(p, 0, maxsz, null);
	}

	public PrefixTreeMultimap(K p) {
		this(p, 0, p.symbols(), null);
	}

	/************************************************************************
	 * public class PrefixTree
	 ************************************************************************/

	protected PrefixTreeMultimap<K, V> makeSubTree(int msym) {
		return new PrefixTreeMultimap<K, V>((K)prefix.spawn(preflen, msym), preflen+1, sizeMax, this);
	}

	protected Set<K> keySetLocal() {
		return tmap.keySet();
	}

	protected int sizeLocal() {
		return tmap.size();
	}

	protected void transferLocalToSubtree(int i, K key) {
		child[i].putAll(key, tmap.removeAll(key));
	}

	protected void transferSubtreeToLocal(PrefixTree<K, V> ch) {
		tmap.putAll(((PrefixTreeMultimap)ch).tmap);
	}

	protected SetMultimap<K, V> selectNode(int i) {
		return (child[i] == null)? tmap: child[i];
	}

	/************************************************************************
	 * public interface Multimap
	 ************************************************************************/

	public Map<K, Collection<V>> asMap() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public void clear() {
		for (int i=0; i<child.length; ++i) {
			child[i] = null;
		}
		subtrees = 0;
		tmap.clear();
	}

	public boolean containsEntry(Object key, Object value) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean containsKey(Object key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.containsKey(k);
	}

	public boolean containsValue(Object value) {
		if (tmap.containsValue(value)) { return true; }
		for (PrefixTreeMultimap<K, V> t: child) {
			if (t.containsValue(value)) { return true; }
		}
		return false;
	}

	public Set<Map.Entry<K,V>> entries() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean equals(Object o) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<V> get(K key) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return null; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		return map.get(k);
	}

	public int hashCode() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean isEmpty() {
		if (!tmap.isEmpty()) { return false; }
		for (PrefixTreeMultimap<K, V> t: child) {
			if (!t.isEmpty()) { return false; }
		}
		return true;
	}

	public Multiset<K> keys() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<K> keySet() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean put(K key, V value) {
		if (!key.match(prefix, preflen)) {
			throw new IllegalArgumentException("Key does not match prefix for this tree.");
		}

		int i = key.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.put(key, value);

		if (!v) { return v; } // size hasn't changed, do nothing
		++sizePrefix[i]; ++size;

		reshuffleAfterPut(i);
		return v;
	}

	public boolean putAll(K key, Iterable<? extends V> values) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean putAll(Multimap<? extends K,? extends V> multimap) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public boolean remove(Object key, Object value) {
		K k; if (!(key instanceof PrefixKey) ||
			!(k = (K) key).match(prefix, preflen)) { return false; }

		int i = k.get(preflen);
		SetMultimap<K, V> map = selectNode(i);
		boolean v = map.remove(key, value);

		if (!v) { return v; } // size hasn't changed, do nothing
		--sizePrefix[i]; --size;

		reshuffleAfterRemove(i);
		return v;
	}

	public Set<V> removeAll(Object key) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public Set<V> replaceValues(K key, Iterable<? extends V> values) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	public int size() {
		return size;
	}

	public Collection<V> values() {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
