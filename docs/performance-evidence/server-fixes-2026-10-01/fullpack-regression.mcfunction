scoreboard objectives add mag_regress dummy
scoreboard players set failures mag_regress 0
fill -3 78 -3 8 85 4 air
fill -2 79 -2 7 79 2 glass
setblock 0 80 0 magnetization:ferrofluid
setblock 1 80 0 magnetization:ferrofluid
setblock 2 80 0 magnetization:ferrofluid
setblock 3 80 0 magnetization:ferrofluid
setblock 4 80 0 magnetization:ferrofluid
setblock -1 80 0 redstone_block
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=14] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=13] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=12] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=11] run scoreboard players add failures mag_regress 1
setblock 2 80 0 air
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=14] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
setblock 2 80 0 magnetization:ferrofluid
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=14] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=13] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=12] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=11] run scoreboard players add failures mag_regress 1
setblock 5 80 0 redstone_block
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=13] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
setblock -1 80 0 air
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=11] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=12] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=13] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=14] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
setblock 5 80 0 air
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
setblock -1 80 0 stone
setblock -2 80 0 lever[face=wall,facing=west,powered=true]
# Command placement does not invoke player lever-use callbacks. Trigger a neighbor update, as in the GameTest.
setblock 0 80 1 glass
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=15] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=14] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=13] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=12] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=11] run scoreboard players add failures mag_regress 1
setblock -2 80 0 air
setblock 0 80 1 air
execute unless block 0 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 1 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 2 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 3 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute unless block 4 80 0 magnetization:ferrofluid[signal_power=0] run scoreboard players add failures mag_regress 1
execute if score failures mag_regress matches 0 run say MAG_FULLPACK_REGRESSION_PASS
execute unless score failures mag_regress matches 0 run say MAG_FULLPACK_REGRESSION_FAIL
scoreboard players get failures mag_regress
