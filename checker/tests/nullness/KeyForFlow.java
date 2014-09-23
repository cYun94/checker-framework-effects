import org.checkerframework.checker.nullness.qual.*;

import java.util.*;

public class KeyForFlow extends HashMap<String, Object> {

  String k = "key";
  HashMap<String,Object> m = new HashMap<String,Object>();

  void testContainsKeyForLocalKeyAndLocalMap() {
    String k_local = "key";
    HashMap<String,Object> m_local = new HashMap<String,Object>();

    if (m_local.containsKey(k_local)) {
      @KeyFor("m_local") Object s = k_local;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m_local") String s2 = k_local;
  }

  void testContainsKeyForLocalKeyAndFieldMap() {
    String k_local = "key";

    if (m.containsKey(k_local)) {
      @KeyFor("m") Object s = k_local;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m") String s2 = k_local;
  }

  void testContainsKeyForFieldKeyAndLocalMap() {
    HashMap<String,Object> m_local = new HashMap<String,Object>();

    if (m_local.containsKey(k)) {
      @KeyFor("m_local") Object s = k;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m_local") String s2 = k;
  }

  void testContainsKeyForFieldKeyAndFieldMap() {
    if (m.containsKey(k)) {
      @KeyFor("m") Object s = k;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m") String s2 = k;
  }

  static String k_s = "key";

  void testContainsKeyForStaticKeyAndFieldMap() {
    if (m.containsKey(k_s)) {
      @KeyFor("m") Object s = k_s;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m") String s2 = k_s;
  }

  static HashMap<String,Object> m_s = new HashMap<String,Object>();

  void testContainsKeyForFieldKeyAndStaticMap() {
    if (m_s.containsKey(k)) {
      // Currently for this to work, the user must write @KeyFor("classname.static_field")
      @KeyFor("m_s") Object s = k;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("m_s") String s2 = k;
  }

  void testContainsKeyForFieldKeyAndReceiverMap() {
    if (containsKey(k)) {
      @KeyFor("this") Object s = k;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("this") String s2 = k;
  }

  Vector<@KeyFor("m2") String> coll = new Vector<@KeyFor("m2") String>();
  HashMap<String,Object> m2 = new HashMap<String,Object>();
  String k2 = "key2";

  void testCallingPutAfterAdd() {
    //:: error: (argument.type.incompatible)
    coll.add(k2);
    m2.put(k2, new Object());
  }

  void testPutForLocalKeyAndLocalMap() {
    Vector<@KeyFor("m2_local") String> coll_local = new Vector<@KeyFor("m2_local") String>();
    HashMap<String,Object> m2_local = new HashMap<String,Object>();
    String k2_local = "key2";

    m2_local.put(k2_local, new Object());
    coll_local.add(k2_local);
  }

  void testPutForLocalKeyAndFieldMap() {
    String k2_local = "key2";

    m2.put(k2_local, new Object());
    coll.add(k2_local);
  }

  void testPutForFieldKeyAndLocalMap() {
    Vector<@KeyFor("m2_local") String> coll_local = new Vector<@KeyFor("m2_local") String>();
    HashMap<String,Object> m2_local = new HashMap<String,Object>();

    m2_local.put(k2, new Object());
    coll_local.add(k2);
  }

  void testPutForFieldKeyAndFieldMap() {
    m2.put(k2, new Object());
    coll.add(k2);
  }

  /*
  This scenario is not working since in Vector, "this" gets translated to "coll_local".
  The same thing happens if the collection is a field instead of a local.
  However this seems like a low-priority scenario to enable.

  void testPutForFieldKeyAndReceiverMap() {
    Vector<@KeyFor("this") String> coll_local = new Vector<@KeyFor("this") String>();

    put(k2, new Object());
    coll_local.add(k2);
  }*/

  class foo {
    public HashMap<String,Object> m = new HashMap<String,Object>();
  }

  void testContainsKeyForFieldKeyAndMapFieldOfOtherClass() {
    foo f = new foo();

    if (f.m.containsKey(k)) {
      @KeyFor("f.m") Object s = k;
    }

    //:: error: (assignment.type.incompatible)
    @KeyFor("f.m") String s2 = k;
  }

  void testPutForFieldKeyAndMapFieldOfOtherClass() {
    Vector<@KeyFor("f.m") String> coll_local = new Vector<@KeyFor("f.m") String>();
    foo f = new foo();
    f.m.put(k2, new Object());
    coll_local.add(k2);
  }

}
