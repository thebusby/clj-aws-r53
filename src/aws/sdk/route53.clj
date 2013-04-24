(ns aws.sdk.route53
  "Functions to access the Amazon Route 53 DNS service.

  Each function takes a map of credentials as its first argument. The
  credentials map should contain an :access-key key and a :secret-key
  key."

  (:import com.amazonaws.AmazonServiceException
           com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.route53.AmazonRoute53Client
           com.amazonaws.services.route53.model.Change
           com.amazonaws.services.route53.model.ChangeBatch
           com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
           com.amazonaws.services.route53.model.GetHostedZoneRequest
           com.amazonaws.services.route53.model.HostedZone
           com.amazonaws.services.route53.model.ListResourceRecordSetsRequest
           com.amazonaws.services.route53.model.ResourceRecord
           com.amazonaws.services.route53.model.ResourceRecordSet
           com.amazonaws.AmazonServiceException
           )

  (:require [clojure.string :as string]))


(defn- route53-client*
  "Create an AmazonRoute53Client instance from a map of credentials."
  [cred]
  (if (:access-key cred)
    (AmazonRoute53Client.
     (BasicAWSCredentials.
      (:access-key cred)
      (:secret-key cred)))
    (AmazonRoute53Client.)))

(def ^{:private true}
  route53-client
  (memoize route53-client*))


;;
;; convert object graphs to clojure maps
;;

(defprotocol ^{:no-doc true} Mappable
  "Convert a value into a Clojure map."
  (^{:no-doc true} to-map [x] "Return a map of the value."))

(extend-protocol Mappable nil (to-map [_] nil))


;;
;; convert clojure maps to object graphs

(defn- keyword-to-method
  "Convert a dashed keyword to a CamelCase method name"
  [kw]
  (apply str (map string/capitalize (string/split (name kw) #"-"))))

(defn set-fields
  "Use a map of params to call setters on a Java object"
  [obj params]
  (doseq [[k v] params]
    (let [method-name (str "set" (keyword-to-method k))
          method (first (clojure.lang.Reflector/getMethods (.getClass obj) 1 method-name false))
          arg-type (first (.getParameterTypes method))
          arg (if (= arg-type java.lang.Integer) (Integer. v) v)]
      (clojure.lang.Reflector/invokeInstanceMember method-name obj arg)))
  obj)

(declare mapper)

(defn map->ObjectGraph
  "Transform the map of params to a graph of AWS SDK objects"
  [params]
  (let [keys (keys params)]
    (zipmap keys (map #((mapper %) (params %)) keys))))

(defmacro mapper->
  "Creates a function that invokes set-fields on a new object of type
   with mapped parameters."
  [type]
  `(fn [~'params] (set-fields (new ~type) (map->ObjectGraph ~'params))))

(defn- mapper
  ""
  [key]
  (let [mappers {}]
    (if (contains? mappers key) (mapper key) identity)))


;;
;; exceptions
;;

(extend-protocol Mappable
  AmazonServiceException
  (to-map [e]
    {:error-code   (.getErrorCode e)
     :error-type   (.name (.getErrorType e))
     :service-name (.getServiceName e)
     :status-code  (.getStatusCode e)}))

(defn decode-exceptions
  "Returns a Clojure map containing the details of an AmazonServiceException"
  [& exceptions]
  (map to-map exceptions))


;;
;; zones
;; 

(extend-protocol Mappable
  HostedZone
  (to-map [hz]
    {:id (.getId hz)
     :name (.getName hz)
     :caller-reference (.getCallerReference hz)
     :config-comment (.getComment (.getConfig hz))})
  ResourceRecordSet
  (to-map [rrs]
    {:name (.getName rrs)
     :region (.getRegion rrs)
     :resource-records (map (fn [^ResourceRecord x] (.getValue x)) (.getResourceRecords rrs))
     :set-identifier (.getSetIdentifier rrs)
     :ttl (.getTTL rrs)
     :type (.getType rrs)
     :weight (.getWeight rrs)}))

(defn list-hosted-zones
  "List hosted zones and their zone-id's.

   E.g.:
   (list-hosted-zones cred)

   Structure returned will appear like:
   ({:id \"Z119FXR5L1SM7T\", 
     :name \"domain.com.\", 
     :caller-reference \"E7CB34E9-E1B9-0096-B6D9-F6D256654A0E\", 
     :config-comment \"Comment in here\"})"
  [cred]
  (map to-map (.getHostedZones (.listHostedZones (route53-client cred)))))

(defn list-resource-record-sets 
  "List zone resource record sets.

   E.g.:
   (list-resource-record-sets cred \"Z119FXR5L1SM7T\")

   Structure returned will appear like:
    ({:name \"domain.com.\",
      :region nil,
      :resource-records (\"ns-1016.awsdns-63.net.\" ... ),
      :set-identifier nil,
      :ttl 172800,
      :type \"NS\",
      :weight nil}
     {:name \"domain.com.\",
      :region nil,
      :resource-records (\"ns-1016.awsdns-63.net. awsdns-hostmaster.amazon.com. 1 7200 900 1209600 86400\"),
      :set-identifier nil,
      :ttl 900,
      :type \"SOA\",
      :weight nil}
     {:name \"www.domain.com.\",
      :region nil,
      :resource-records (\"ec2-50-14-27-88.compute-1..amazonaws.com\"),
      :set-identifier nil,
      :ttl 300,
      :type \"CNAME\",
      :weight nil}
      ...)"
  [cred zone-id]
  (map to-map (.getResourceRecordSets (.listResourceRecordSets (route53-client cred) (ListResourceRecordSetsRequest. zone-id)))))

(defn change-resource-record-sets
  "Apply DNS changes to a zone.

   E.g.:
   (change-resource-record-sets cred \"Z119FXR5L1SM7T\"
                                (create-change \"CREATE\" {:type \"CNAME\" 
                                                           :name \"search.domain.com.\" 
                                                           :resource-records [(create-resource-record \"www.google.com\") ]
                                                           :t-t-l (long 300)}))"
  [cred zone-id & changes]
  (.changeResourceRecordSets (route53-client cred) (ChangeResourceRecordSetsRequest. zone-id (ChangeBatch. changes))))

(defn create-resource-record
  "Create a ResourceRecord object containing a single value.

   E.g.:
   (create-resource-record \"127.0.0.1\")"
  [value]
  (ResourceRecord. value))

(defn create-change
  "Create a DNS change record. 
   Note: 'TTL' needs to use the :t-t-l keyword, and it's value must be a Long.

   E.g.:
    (create-change \"DELETE\" {:type \"A\" 
                               :name \"localhost.domain.com.\" 
                               :resource-records [(create-resource-record \"127.0.0.1\") ]
                               :t-t-l (long 300)}))"
  [action resource-params]
     (Change. action ((mapper-> ResourceRecordSet) resource-params)))
