(ns forge-clj.util
  "Large variety of utility functions and macros that are just plain useful.
  Available to both Client and Server."
  (:require
   [clojure.string :as string]
   [clojure.set :as cset]
   [forge-clj.core :refer [defobj]])
  (:import
   [java.util Random]
   [net.minecraft.block Block]
   [net.minecraft.item Item ItemStack]
   [net.minecraft.creativetab CreativeTabs]
   [net.minecraft.entity.player EntityPlayer]
   [net.minecraft.entity.item EntityItem]
   [net.minecraft.util ChatComponentText Vec3 MovingObjectPosition]
   [net.minecraft.inventory IInventory]
   [net.minecraft.server MinecraftServer]
   [net.minecraft.world World]
   [cpw.mods.fml.common.registry GameRegistry]))

(defn server-worlds
  "Gets an array of server-side worlds currently used."
  []
  (.-worldServers ^MinecraftServer (MinecraftServer/getServer)))

(defmulti make-itemstack
  "Multimethod used by itemstack to make itemstacks."
  (fn [item amount metadata] (type item)))
(defmethod make-itemstack Item [^Item item amount metadata]
  (ItemStack. item (int amount) (int metadata)))
(defmethod make-itemstack Block [^Block block amount metadata]
  (ItemStack. block (int amount) (int metadata)))

(defn itemstack
  "Makes an ItemStack. For convenience."
  ([item]
   (itemstack item 1))
  ([item amount]
   (itemstack item amount 0))
  ([item amount metadata]
   (make-itemstack item amount metadata)))

(defn remote?
  "Convenient .isRemote check function. Looks cleaner."
  [^World world]
  (.isRemote world))

(defn update-map-vals
  "Utility function. Given a map and a function, applies that function to all values in the map."
  [func m]
  (into {} (map #(vector (key %1) (func (val %1))) m)))

(defn update-map-keys
  "Utility function. Given a map and a function, applies that function to all keys in the map."
  [func m]
  (cset/map-invert (update-map-vals func (cset/map-invert m))))

(defn abs
  "Extremely basic function that returns the absolute value of a number. For convenience."
  [n]
  (if (< n 0)
    (* -1 n)
    n))

(defmacro defmemo
  "MACRO: same as (def <name> (memoize (fn ...))).
  In otherwords, it's a def version of the memoize function.

  Memoized functions are similar to normal functions, but can be more performant in certain circumstances.
  They basically keep a record of each arguments and their return value,
  so if a function is called with the same arguements twice, it will run through the calculations the first time,
  cache the result, and the second time it'll just return the cached result without running the function again.

  Of course, this means that this function has to be a pure function,
  which means that it has no side effects (no setters, etc.), and doesn't rely on mutable external values,
  such that it always returns the same thing if called with the same arguements.

  You can read more about this at clojure.org docs under the \"memoize\" function."
  [memo-name arg-vector & args]
  `(def ~memo-name
     (memoize
      (fn ~arg-vector
        ~@args))))

(defmacro deftab
  "DEFOBJ: Creates an anonymous instance of CreativeTabs."
  [tab-name & args]
  (let [obj-data (apply hash-map args)]
    `(defobj CreativeTabs [~(str tab-name)] ~tab-name ~obj-data)))

(defn get-item
  "Given the mod-id and the item name separated by a :,
  or the mod-id and the item name as two separate arguements,
  attempts to get the item from the GameRegistry with the specified name."
  ([name-and-id]
   (let [split-id (string/split name-and-id #":")]
     (get-item (first split-id) (second split-id))))
  ([modid item-name]
   (GameRegistry/findItem (str modid) (str item-name))))

(defn get-block
  "Given the mod-id and the block name separated by a :,
  or the mod-id and the block name as two separate arguements,
  attempts to get the block from the GameRegistry with the specified name."
  ([name-and-id]
   (let [split-id (string/split name-and-id #":")]
     (get-block (first split-id) (second split-id))))
  ([modid block-name]
   (GameRegistry/findBlock (str modid) (str block-name))))

(defn printchat
  "Given a player and a string, prints out a message to their chat."
  [^EntityPlayer player s]
  (.addChatComponentMessage player (ChatComponentText. (str s))))

(defn drop-items [^World world x y z]
  "Given the world, x, y, and z coordinates of a tile entity implementing IInventory, drops the items contained in the inventory."
  (let [tile-entity (.getTileEntity world (int x) (int y) (int z))]
    (if (instance? IInventory tile-entity)
      (let [tile-entity ^IInventory tile-entity
            per-stack (fn [^ItemStack istack]
                        (if (and istack (< 0 (.-stackSize istack)))
                          (let [rand-x (+ (* (rand) 0.8) 0.1)
                                rand-y (+ (* (rand) 0.8) 0.1)
                                rand-z (+ (* (rand) 0.8) 0.1)
                                entity-item (EntityItem. world (+ x rand-x) (+ y rand-y) (+ z rand-z) (itemstack (.getItem istack) (.-stackSize istack) (.getItemDamage istack)))]
                            (if (.hasTagCompound istack)
                              (.setTagCompound (.getEntityItem entity-item) (.copy (.getTagCompound istack))))
                            (set! (.-motionX entity-item) (* (rand) 0.05))
                            (set! (.-motionY entity-item) (+ (* (rand) 0.05) 0.2))
                            (set! (.-motionZ entity-item) (* (rand) 0.05))
                            (.spawnEntityInWorld world entity-item)
                            (set! (.-stackSize istack) 0))))]
        (doall (map #(per-stack (.getStackInSlot tile-entity %1)) (range (.getSizeInventory tile-entity))))))))

(defn get-look-coords
  "Given the current player and the world, gets the coordinates and the block side hit that the player is looking at."
  [^EntityPlayer player ^World world]
  (let [^Vec3 pos-vec (Vec3/createVectorHelper (.-posX player) (+ (.-posY player) (.getEyeHeight player)) (.-posZ player))
        ^Vec3 look-vec (.getLookVec player)
        ^MovingObjectPosition mop (.rayTraceBlocks world pos-vec look-vec)]
    [(.-blockX mop) (.-blockY mop) (.-blockZ mop) (.-sideHit mop)]))

(defn construct
  "Given a class and any arguments to the constructor, makes an instance of that class.
  Not a macro like Clojure's new keyword, so can be used with class names that are stored in symbols."
  [klass & args]
  (clojure.lang.Reflector/invokeConstructor klass (into-array Object args)))
