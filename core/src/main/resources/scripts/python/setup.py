from setuptools import setup
from pathlib import Path
import re

setup(
    name='kestra',
    version=re.search('(?m)^version=(.*)$', (Path(__file__).parent / 'gradle.properties').read_text()).group(1).replace('-SNAPSHOT', ''),
    package_dir={'': 'src'},
    include_package_data=True,
    python_requires='>=3',
    description='Kestra is an infinitely scalable orchestration and scheduling platform, creating, running, scheduling, and monitoring millions of complex pipelines.',
    long_description=(Path(__file__).parent / 'README.md').read_text(),
    long_description_content_type='text/markdown',
    url='https://kestra.io',
    author='Kestra',
    author_email='hello@kestra.io',
    license='Apache License 2.0',
    platforms='any',
    classifiers=[
        'License :: OSI Approved :: Apache Software License',
        'Programming Language :: Python',
        'Programming Language :: Python :: 3',
    ],
)
