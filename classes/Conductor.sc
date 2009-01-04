Conductor : Environment {
	classvar <>specs;
	var <>valueKeys, <path;
	var <>gui;			// defines gui display of conductor in windows
	var <>player;
	var <>preset;
	
	*initClass {
		StartUp.add ({
			Conductor.specs = IdentityDictionary.new;
			Conductor.specs.putPairs([
				// set up some ControlSpecs for common mappings
				// you can add your own after the fact.
				
				unipolar: 	ControlSpec(0, 1),
				bipolar: 		ControlSpec(-1, 1, default: 0),
	
				freq: 		ControlSpec(20, 20000, \exp, 0, 440, units: " Hz"),
				lofreq: 		ControlSpec(0.1, 100, \exp, 0, 6, units: " Hz"),
				midfreq: 		ControlSpec(25, 4200, \exp, 0, 440, units: " Hz"),
				widefreq: 	ControlSpec(0.1, 20000, \exp, 0, 440, units: " Hz"),
				phase: 		ControlSpec(0, 2pi),
				rq: 			ControlSpec(0.001, 2, \exp, 0, 0.707),
	
				audiobus: 	ControlSpec(0, 127, step: 1),
				controlbus: 	ControlSpec(0, 4095, step: 1),
				in: 			ControlSpec(0, 4095, step: 1),
				fin: 		ControlSpec(0, 4095, step: 1),
	
				midi: 		ControlSpec(0, 127, default: 64),
				midinote: 	ControlSpec(0, 127, default: 60),
				midivelocity: ControlSpec(1, 127, default: 64),
				
				
				dbamp: 		ControlSpec(0.ampdb, 1.ampdb, \db, units: " dB"),
				amp: 		ControlSpec(0, 1, \amp, 0, 0),
				boostcut: 	ControlSpec(-20, 20, units: " dB",default: 0),
				db: 			ControlSpec(-100, 20, default: -20),
				
				pan: 		ControlSpec(-1, 1, default: 0),
				detune: 		ControlSpec(-20, 20, default: 0, units: " Hz"),
				rate: 		ControlSpec(0.125, 8, \exp, 0, 1),
				beats: 		ControlSpec(0, 20, units: " Hz"),
				ratio: 		ControlSpec(1/64, 64, \exp, 0, 1),
				dur: 		ControlSpec(0.01, 10, \exp, 0, 0.25),
				
				delay: 		ControlSpec(0.0001, 1, \exp, 0, 0.3, units: " secs"),
				longdelay: 	ControlSpec(0.001, 10, \exp, 0, 0.3, units: " secs"),
	
				fadeTime: 	ControlSpec(0.001, 10, \exp, 0, 0.3, units: " secs")
				
			]);
	 })
	}
	
	*postSpecs {
		var sp;
		specs.keys.asSortedList.do { | key |
			sp = Conductor.specs[key];
			if (sp.class == ControlSpec) { 
				key.postL(15);
				sp.default.postL;
				sp.minval.postL;
				sp.maxval.postL;
				sp.warp.class.postLn(25);
			}
		}
	}
	*make { arg func; 
		var obj, args, names;
		obj = this.new;
		^obj.make(func)
	}
	
	*new { ^super.new.init }
	
	init {
		gui = ConductorGUI(this, #[ ]);
		this[\player] = player = ConductorPlayer(this);
		this.noSettings;
	}
			
	make { arg func; 
		var obj, args, names;


		#args, names = this.makeArgs(func);
		valueKeys = valueKeys ++ names;
		gui.keys_( gui.keys ++ names);
		this.usePresets;
		super.make({func.valueArray(this, args)});
	}

	*makeCV { | name, value |
		^CV(specs[name.asString.select{ | c | c.isAlpha}.asSymbol], value)
	}
	makeArgs { arg func;
		var argList, size, names, argNames;
		var theClassName, name, obj;
		
		size = func.def.argNames.size;
		argList = Array(size);
		argNames = Array(size);
		names = func.def.argNames;
		// first arg is Event under constructions, subsequent are CVs or instances of other classes
		if (size > 1, {
			1.forBy(size - 1, 1, { arg i;
				name = names[i];
				argNames = argNames.add(name);
				theClassName = func.def.prototypeFrame.at(i);
				if (theClassName.notNil) {
					obj = theClassName.asClass.new;
				} {
					obj = Conductor.makeCV(name)
				};
				this.put(name,obj);
				argList = argList.add(obj);
			});
		});
		^[argList, argNames];
		
	}

// saving and restoring state 
	getFile { arg argPath; var file, contents;
		if (File.exists(argPath)) {
			path = argPath;
			file = File(path,"r"); 
			contents = file.readAllStringRTF;
			file.close;
			^contents;
		} {
			(argPath + "not found").postln;
			^nil
		}
	}
	
	putFile { | vals, argPath | 
		path = argPath ? path;
		File(path,"w").putAll(vals).close;
	}

	load { | argPath |
		var v;
		if (argPath.isNil) {
			File.openDialog(nil, { arg path; 
				v = this.getFile(path);
				this.value_(v.interpret)
			});
		} {
			v = this.getFile(argPath);
			this.value_(v.interpret)
		};
	}
	
	save { | path |
		if (path.isNil) {
			File.saveDialog(nil, nil, { arg path; 
				this.putFile(this.value.asCompileString, path)
			});
		} {
			this.putFile(this.value.asCompileString, path)
		};

	}

	path_ { | path |
		this.load(path);
	}

// gui display of file saving
	noSettings { this[\settings] = nil; this[\preset] = NullPreset; }
	
	useSettings { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = NullPreset;
	}
	
	usePresets { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = preset =  CVPreset.new; 
		this.presetKeys_(valueKeys);		
	}
	
	useInterpolator { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = preset =  CVInterpolator.new; 
		this.presetKeys_(valueKeys);
		this.interpKeys_(valueKeys);
	}
	
// interface to default preset and interpolator

	presetKeys_ { | keys, argPreset |
		argPreset = argPreset ? preset;
		preset.items = keys.collect { | k | this[k] };
	}
	
	interpKeys_ { | keys, argPreset |
		argPreset = argPreset ? preset;
		argPreset.interpItems = keys.collect { | k | this[k] };
	}

	input {  var keys;
		if (this[\preset].notNil) { keys = #[preset] };
		^(valueKeys ++ keys).collect { | k| [k, this[k].input ]  }  }
		
	input_ { | kvs | kvs.do { | kv| this[kv[0]].input_(kv[1]); kv[0]; } }
	
	value {  ^(valueKeys ++ #[preset]).collect { | k| [k, this[k].value ]  }  }
	
	value_ { | kvs | kvs.do { | kv| this[kv[0]].value_(kv[1]); kv[0]; } }
	
//gui interface
	show { arg argName, x = 128, y = 64, w = 900, h = 160;
		^gui.show(argName, x, y, w, h);
	}

	draw { | win, name, conductor|
		gui.draw (win, name, conductor)
	}
	
	
// play/stop/pause.resume
	stop {
		player.stop;
 	}
	
	play { 
		player.play;		
	}
 

	pause { 
		player.pause; 
	}

	resume { 	
		player.resume; 
	}

	name_ { | name | player.name_(name) }
	
	name { ^player.name }
	
//player interface

	add { | object |
		player.add(object)
	}
	
	action_ { | playFunc, stopFunc, pauseFunc, resumeFunc |
		this.add ( ActionPlayer(playFunc, stopFunc, pauseFunc, resumeFunc ) )
	}

	buffer_ { | ev| 
		ev.parent = CVEvent.bufferEvent;
		this.add(ev);
	}
	
	controlBus_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.controlBusEvent;
		this.add(ev)
	}

	synth_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.synthEvent;
		this.add(ev)
	}

	synthDef_ { | function, cvs, ev|
		var name;
		name = function.hash.asString;
		SynthDef(name, function).store;
		ev = ev ? ();
		ev	.put(\instrument, name)
			.put(\cvs, cvs);
		ev.parent_(CVEvent.synthEvent);
		this.add(ev);
		^ev
	}

	group_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.groupEvent;
		this.add(ev)
	}
	
	task_ { |func, clock, quant|
		this.add(TaskPlayer(func,clock, quant));
	}
	
	pattern_ { | pat, clock, event, quant |
		this.add(PatternPlayer(pat, clock, event, quant) )
	}

		


//	nodeProxy_ { | nodeProxy, args, bus, group |
//		this.add(NodeProxyPlayer(nodeProxy, args, bus, group) )
//	}
//	
	addCon { | name, func|
		var con;
		name = name.asSymbol;
		con = Conductor.new;
		con.name_(name);
		this.put(name, con.make(func) );
		gui.addKeys( [name] );
	}
	
	addCV { | name, val |
		var cv, v;
		cv = Conductor.makeCV(name, val);
		this.put(name, cv);
		if (preset.notNil) { preset.items = preset.items.add(cv) };
		gui.addKeys( [name] );
		^cv;
						
	}

	updateNPCV {| nodeProxy, key, value |
		var cv;
		
		if ( (cv = this[key]).notNil) { 
			if (value.notNil) { cv.value_(value) }
		} {
			cv = this.addCV(key, value);
			cv.action_({ nodeProxy.prset(key, cv.value) });
			nodeProxy.prset(key, cv.value);
		}		
	}

	nodeProxy_ { | nodeProxy, args, bus, numChannels, group, multi = false |
		this.add(NodeProxyPlayer(nodeProxy, args, bus, numChannels, group, multi) )
	}


	useMIDI { 
		var conductor = this;
		if (conductor.valueKeys.includes(\mappings).not) {
			conductor.valueKeys = conductor.valueKeys ++ \mappings;
			conductor[\mappings] = Ref( () );
		};
		conductor.gui.guis.put('map MIDI', 
			 { |win, name, interp|
			 	var w;
				~simpleButton.value(win, Rect(0,0,60, 20))
					.states_([["map MIDI", Color.black, Color.hsv(0, 0.5,1)]])
					.action_({ var cond;
						MIDIIn.connectAll;
						if (w.isNil) {
						cond = Conductor.make({ | con |
							var keys, ccAssigns, kdAssigns;
							con.gui.header = [];
							~ccAssigns = ccAssigns = ();
							con.noSettings;
							con.name_("MIDI mapper");
							keys = conductor.gui.keys.select{ | k | conductor[k].class === CV };
							
							keys.do({ | k | con.addCV(k) });
							keys.do({ | k | con[k].sp(0,0,2,1) });
							con.gui.use {
								~cvGUI = ~radiobuttons
							};
							
							
							con.task_( { var ev, packet, activeKeys;
									loop {
									ev = MIDIIn.waitControl;
									packet = ev.chan * 128 + ev.b;
									activeKeys = keys.select { | k | con[k].value == 1 };
									if (activeKeys.size > 0) {
										conductor[\mappings].value.put(packet, activeKeys.copy);
									defer {
										activeKeys.do { | k | con[k].value = 2 }
									}
								}
							};
						
							
							})
						});
						w = cond.show("MIDImap", win.bounds.left + win.bounds.width, win.bounds.top, 200, 300);
						cond.play;
						defer ({ w.bounds = w.bounds.resizeBy(50, 0)}, 0.02) ;
						topEnvironment[\w] = w;
					} {
						w.close; w = nil;
					};
				});
			}		
		);
		conductor.gui.guis.put(\midi, 
		 { |win, name, interp|
		 	var routine;
			~simpleButton.value(win, Rect(0,0,60, 20))
				.states_([["midi", Color.black, Color.hsv(0, 0.7,1)], ["midi", Color.red, Color.black] ])
				.action_({ |bt |
					if (bt.value == 0) {
						routine.stop; routine.originalStream.stop; routine = nil;
					} {
						MIDIIn.connectAll;
						routine = Task { var ev,keys;
							loop {
								ev = MIDIIn.waitControl;
								if ( (keys = conductor[\mappings].value[ev.chan * 128 + ev.b]).notNil) {
									keys.do { | key |
										conductor[key].input_(ev.c/127);
									}
								}	
							}
						}.play;
					}
				});
			});			

		conductor.gui.header = [ conductor.gui.header[0] ++ \midi ++ 'map MIDI'];
	}	
}
