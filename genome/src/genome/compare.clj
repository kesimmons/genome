(ns genome.compare
  (require [clojure.java.io   :as io ]
           [incanter.core     :as i  ]
           [incanter.datasets :as id ]
           [incanter.io       :as ii ]
           [incanter.charts   :as c  ]
           [incanter.stats    :as st ]
           [clojure.string    :as s  ]
           [clojure.data.csv  :as csv]
           [genome.database   :as gd ]
           [genome.stats      :as gs ]
           [genome.pop        :as p  ]
           [genome.consvar    :as cv ]
           [genome.dna2aa     :as da ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;TESTING SNP TREND
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn calc-coved [file]
  "Calculation corrected coverage"
  (->> file
       (i/add-derived-column
        :p_cov
        [:Tpois :Apois :Gpois :Cpois]
        #(double (+ %1 %2 %3 %4)))))

(defn snp-precent [snp file]
  "snp sould be the string of the keyword"
  (->> file
       (i/add-derived-column
        (keyword (str snp "-fq"))
        [(keyword snp) :p_cov]
        #(if (= %2 0.0)
           0.0
           (double (/ %1 %2))))))

(defn add-snp-precent [file]
  (->> file
       (snp-precent "Tpois")
       (snp-precent "Cpois")
       (snp-precent "Apois")
       (snp-precent "Gpois")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;DEFINE NEW COLUMN NAME
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sample1 {:Tpois :T1 :Tpois-fq :Tfq1
              :Cpois :C1 :Cpois-fq :Cfq1
              :Apois :A1 :Apois-fq :Afq1
              :Gpois :G1 :Gpois-fq :Gfq1
              :p_cov :cov1 :pi_pois :pi1
              :consus_un :p-sus1
              :negsus_un :n-sus1
              :aa_fwd :aa_fwd1 :aa_rev :aa_rev1})

(def sample2 {:Tpois :T2 :Tpois-fq :Tfq2
              :Cpois :C2 :Cpois-fq :Cfq2
              :Apois :A2 :Apois-fq :Afq2
              :Gpois :G2 :Gpois-fq :Gfq2
              :p_cov :cov2 :pi_pois :pi2
              :consus_un :p-sus2
              :negsus_un :n-sus2
              :aa_fwd :aa_fwd2 :aa_rev :aa_rev2})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;UNITE TWO DATASET AT COMMON SITES
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn unite [file1 file2]
  "Adds only file2 rows that have a common :loc value with file1"
  (let [coled1 (i/rename-cols sample1 file1)
        coled2 (i/rename-cols sample2 file2)]
    (i/$where {:A2 {:$ne nil}}
              (i/$join [:loc :loc] coled2 coled1))))

(defn create-dataset [file1 file2]
  "creates a dataset which contains all sites with allele frequency"
  (let [p_covd1 (calc-coved file1)
        p_covd2 (calc-coved file2)
        snp1   (add-snp-precent p_covd1)
        snp2   (add-snp-precent p_covd2)]
    (->>(unite snp1 snp2)
        (i/$ [:loc    :cov1   :pi1
              :p-sus1 :n-sus1 :aa_fwd1 :aa_rev1
              :A1     :Afq1   :T1     :Tfq1
              :G1     :Gfq1   :C1     :Cfq1 
                      :cov2   :pi2
              :p-sus2 :n-sus2 :aa_fwd2 :aa_rev2
              :A2     :Afq2   :T2     :Tfq2
              :G2     :Gfq2   :C2     :Cfq2 ])))) 

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;FINAL FUNCTIONS FOR VSRIANTS ALLELE CHANGE AND DIV CHANGE
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn variants [file1 file2]
  "Shows alleles from two samples at same site"
  (->>(create-dataset file1 file2)
      (i/$ [:loc
            :cov1 :A1 :T1 :G1 :C1 :pi1
            :cov2 :A2 :T2 :G2 :C2 :pi2
            :p-sus1  :p-sus2  :n-sus1  :n-sus2
            :aa_fwd1 :aa_fwd2 :aa_rev1 :aa_rev2])))

(defn allele-change [file1 file2]
  "compares all alleles frequencies from two samples at  same site"
  (->>(create-dataset file1 file2)
      (i/$ [:loc
            :cov1 :Afq1 :Tfq1 :Gfq1 :Cfq1
            :cov2 :Afq2 :Tfq2 :Gfq2 :Cfq2])
      ( #(i/conj-rows (i/$ [:cov1 :cov2 :loc :Afq1 :Afq2]  %)
                      (i/$ [:cov1 :cov2 :loc :Tfq1 :Tfq2]  %)
                      (i/$ [:cov1 :cov2 :loc :Cfq1 :Cfq2]  %)
                      (i/$ [:cov1 :cov2 :loc :Gfq1 :Gfq2]  %) ))
      (i/rename-cols {:Afq1 :fq1 :Afq2 :fq2})
      (i/$where (i/$fn [fq1 fq2] (or (> fq1 0.0) (> fq2 0.0)))) 
      (i/$where (i/$fn [fq1 fq2] (or (< fq1 1.0) (< fq2 1.0))))                    
      (i/$order :loc :asc)))

(defn diversity-change [file1 file2]
  "compares diversity change between two sites"
  (->>(create-dataset file1 file2)
      (i/$ [:loc :cov1 :pi1 :cov2 :pi2])
      (i/add-derived-column
       :gap
       [:pi2 :pi1]
       #(- %1 %2))))



    
