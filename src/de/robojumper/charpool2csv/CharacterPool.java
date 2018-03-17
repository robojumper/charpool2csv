package de.robojumper.charpool2csv;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class CharacterPool {

	static final byte[] DEFAULT_HEADER = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
	static final String NAME_NONE = "none";

	byte[] header = new byte[4];

	Integer charPoolCount; // count of the empty Object array that is serialized

	// The Default pool is a bit more messed up than the default pool
	boolean isImportablePool = false;
	String poolFileName;

	CharacterPoolDataElement deadCharacter;

	List<CharacterPoolDataElement> characters = new ArrayList<CharacterPoolDataElement>();

	private ByteBuffer buffer;
	private Stack<ByteBuffer> bufferStack = new Stack<ByteBuffer>();
	private Stack<Object> structStack = new Stack<Object>();

	public CharacterPool(byte[] data) {
		buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		bufferStack.push(buffer);

		buffer.get(this.header);
		assert (Arrays.equals(this.header, DEFAULT_HEADER));

		while (readProperty()) {
		}
		System.out.println("Read file! \\o/");

	}

	private boolean readProperty() {
		if (buffer.remaining() == 0) {
			return false;
		}
		String propName = getUnrealTagFromBuffer().toLowerCase();
		if (propName.equals(NAME_NONE)) {
			if (structStack.isEmpty()) {
				// If we hit a 'None' while we're not deserializing a non-class struct, we must
				// have been deserializing the class! Since we end structs (classes are structs)
				// with a None, we stop deserializing the class now and start reading the
				// appended character pool
				int itemCount = buffer.getInt();
				assert (this.charPoolCount == null || itemCount == this.charPoolCount);
				while (characters.size() < itemCount) {
					// Start reading soldier data:
					CharacterPoolDataElement soldier = new CharacterPoolDataElement();
					structStack.push(soldier);
					while (readProperty()) {

					}
					characters.add(soldier);
					structStack.pop();
				}
			} else {
				// If we are deserializing a struct, a None indicates that the struct is over
				// and we continue with the next element / property
				return false;
			}
		} else {
			String propType = getUnrealTagFromBuffer();
			Integer propDataSize = buffer.getInt();
			buffer.position(buffer.position() + 4);

			switch (propType.toLowerCase()) {
			case "strproperty":
				byte[] innerString = new byte[propDataSize];
				buffer.get(innerString);
				ByteBuffer innerBuffer = ByteBuffer.wrap(innerString).order(ByteOrder.LITTLE_ENDIAN);
				Integer innerStringLength = innerBuffer.getInt();
				String str = "";
				if (innerStringLength > 0) {
					byte[] strData = new byte[innerStringLength];
					innerBuffer.get(strData);
					str = new String(strData, 0, innerStringLength - 1);
				} else if (innerStringLength < 0) {
					// A negative string length indicates that it's a unicode string. For UE,
					// "Unicode" means "every character is two bytes"
					innerStringLength = (-2) * innerStringLength;
					byte[] strData = new byte[innerStringLength];
					innerBuffer.get(strData);
					str = new String(strData, 0, innerStringLength - 2, StandardCharsets.UTF_16LE);
				}
				assert (innerBuffer.remaining() == 0);

				if (structStack.isEmpty()) {
					if (propName.equals("poolfilename")) {
						this.isImportablePool = true;
						this.poolFileName = str;
					}
				} else if (!trySetField(propName, String.class, str)) {
				}
				break;
			case "nameproperty":
				byte[] innerNm = new byte[propDataSize];
				buffer.get(innerNm);
				ByteBuffer innerNmBuffer = ByteBuffer.wrap(innerNm).order(ByteOrder.LITTLE_ENDIAN);
				Integer innerNmLength = innerNmBuffer.getInt();
				String nm = "";
				if (innerNmLength > 0) {
					byte[] nmData = new byte[innerNmLength];
					innerNmBuffer.get(nmData);
					nm = new String(nmData, 0, innerNmLength - 1);
				}
				innerNmBuffer.position(innerNmBuffer.position() + 4);
				assert (innerNmBuffer.remaining() == 0);

				if (!trySetField(propName, String.class, nm)) {

				}
				break;
			case "intproperty":
				Integer data = buffer.getInt();
				// Seems to be serialized because why not?
				if (structStack.isEmpty() && propName.equals("genderhelper")) {
					System.out.println("GenderHelper: " + data);
				} else if (!trySetField(propName, Integer.class, data)) {

				}
				break;
			case "structproperty":
				String structName = getUnrealTagFromBuffer().toLowerCase();
				byte[] structData = new byte[propDataSize];
				buffer.get(structData);
				buffer = bufferStack.push(ByteBuffer.wrap(structData).order(ByteOrder.LITTLE_ENDIAN));
				if (structName.equals("tappearance") && propName.equals("kappearance") && !structStack.isEmpty()
						&& structStack.peek().getClass() == CharacterPoolDataElement.class) {
					CharacterPoolDataElement soldier = (CharacterPoolDataElement) structStack.peek();
					soldier.kAppearance = new TAppearance();
					structStack.push(soldier.kAppearance);
					// Read the appearance struct
					while (readProperty()) {

					}
					structStack.pop();
				} else if (propName.equals("characterpoolserializehelper")
						&& structName.equals("characterpooldataelement") && !isImportablePool) {
					// The default pool has one single character here
					CharacterPoolDataElement soldier = new CharacterPoolDataElement();
					structStack.push(soldier);
					while (readProperty()) {

					}
					// Due to the way the pool is serialized, this is a "dummy character" that
					// doesn't really exist
					deadCharacter = soldier;
					structStack.pop();
				}
				bufferStack.pop();
				buffer = bufferStack.peek();
				break;
			case "boolproperty":
				boolean bool = buffer.get() == 1;
				if (!trySetField(propName, Boolean.class, bool)) {

				}
				break;
			case "arrayproperty":
				if (propName.equals("characterpool")) {
					this.charPoolCount = buffer.getInt();
				}

			}
		}
		return true;
	}

	// Helper reflection function because I can't be bothered to do all of this
	// manually
	private <T> boolean trySetField(String fieldName, Class<T> cls, T value) {
		Object struct = this.structStack.isEmpty() ? null : this.structStack.peek();
		if (struct != null) {
			for (Field f : struct.getClass().getDeclaredFields()) {
				if (f.getName().equalsIgnoreCase(fieldName) && f.getType() == cls) {
					try {
						f.set(struct, value);
						return true;
					} catch (IllegalArgumentException | IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return false;
	}

	// Reads a string from the buffer. An unreal string is an Integer containing the
	// byte length of the following string, followed by a null-terminated string and
	// four null characters(?). The length includes the single null character.
	private String getUnrealTagFromBuffer() {
		Integer length = buffer.getInt();
		// Read the \0, ...
		byte[] str = new byte[length];
		buffer.get(str);
		// Skip four bytes that always seem to be 0x00
		buffer.position(buffer.position() + 4);
		// ... but skip it for the string
		return new String(str, 0, length - 1);
	}

	// Classes containing the actually relevant data
	class CharacterPoolDataElement {
		String strFirstName;
		String strLastName;
		String strNickName;
		String m_SoldierClassTemplateName;
		String CharacterTemplateName;
		TAppearance kAppearance;
		String Country;
		Boolean AllowedTypeSoldier;
		Boolean AllowedTypeVIP;
		Boolean AllowedTypeDarkVIP;
		String PoolTimestamp;
		String BackgroundText;
	}

	class TAppearance {
		String nmHead;
		Integer iGender;
		Integer iRace;
		String nmHaircut;
		Integer iHairColor;
		Integer iFacialHair;
		String nmBeard;
		Integer iSkinColor;
		Integer iEyeColor;
		String nmFlag;
		Integer iVoice;
		Integer iAttitude;
		Integer iArmorDeco;
		Integer iArmorTint;
		Integer iArmorTintSecondary;
		Integer iWeaponTint;
		Integer iTattooTint;
		String nmWeaponPattern;
		String nmPawn;
		String nmTorso;
		String nmArms;
		String nmLegs;
		String nmHelmet;
		String nmEye;
		String nmTeeth;
		String nmFacePropLower;
		String nmFacePropUpper;
		String nmPatterns;
		String nmVoice;
		String nmLanguage;
		String nmTattoo_LeftArm;
		String nmTattoo_RightArm;
		String nmScars;
		String nmTorso_Underlay;
		String nmArms_Underlay;
		String nmLegs_Underlay;
		String nmFacePaint;

		// Added to support armors that allow left arm / right arm customization
		String nmLeftArm;
		String nmRightArm;
		String nmLeftArmDeco;
		String nmRightArmDeco;

		// Added to support Xpack faction hero armor deco
		String nmLeftForearm;
		String nmRightForearm;
		String nmThighs;
		String nmShins;
		String nmTorsoDeco;
		Boolean bGhostPawn;
	}

}
